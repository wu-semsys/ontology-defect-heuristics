package at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.impl

import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.model.DefectCandidateDto
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.impl.CommonFunctions.isEquivalentClass
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.DefectDetectionHeuristic
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.mapper.DetectedCandidateMapper
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.parameters.ChangeApplied
import org.semanticweb.owlapi.reasoner.OWLReasoner
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import uk.ac.manchester.cs.owl.owlapi.OWLDisjointClassesAxiomImpl
import java.time.Instant

@Service
@Qualifier(MissingDisjointnessHeuristic.ERROR_TYPE_NAME)
class MissingDisjointnessHeuristic(
    private val detectedCandidateMapper: DetectedCandidateMapper,
) : DefectDetectionHeuristic {

    companion object {
        val LOG: Logger = LogManager.getLogger()
        const val ERROR_TYPE_NAME = "missing-disjointness"
    }

    override fun getErrorTypeName() = MissingDisjointnessHeuristic.ERROR_TYPE_NAME

    override fun findCandidates(ontology: OWLOntology, reasoner: OWLReasoner): List<DefectCandidateDto> {
        var lastTimestamp = Instant.now().toEpochMilli()
        val startDetection = lastTimestamp

        val unsatisfiableClasses = reasoner.unsatisfiableClasses.toList()

        val nonDefinedClasses: Set<OWLClass> = reasoner.topClassNode.entities().toList()
            .flatMap { topClassNode ->
                reasoner.subClasses(topClassNode, false).toList()
            }
            .minus(unsatisfiableClasses)
            .distinct()
            .filter { ontClass -> !isEquivalentClass(ontology, ontClass) }
            .toSet()

        LOG.debug("found ${nonDefinedClasses.size} non-defined classes in ${Instant.now().toEpochMilli() - lastTimestamp}ms")
        lastTimestamp = Instant.now().toEpochMilli()

        val superClasses: Map<OWLClass, Set<OWLClass>> = nonDefinedClasses
            .associateWith { owlClass ->
                val superClasses = reasoner.superClasses(owlClass).toList()
                    .filter { !it.isOWLThing } // filter out Thing
                    // filter out equivalent classes
                    .filter { superClass -> !isEquivalentClass(ontology, superClass) }
                    .toMutableSet()

                superClasses.add(owlClass)
                return@associateWith superClasses
            }

        LOG.debug("found ${nonDefinedClasses.size} superclass sets in ${Instant.now().toEpochMilli() - lastTimestamp}ms")
        lastTimestamp = Instant.now().toEpochMilli()

        val disjointSets: Map<OWLClass, Set<OWLClass>> = nonDefinedClasses
            .associateWith { reasoner.disjointClasses(it).toList().minus(unsatisfiableClasses).toSet() }

        LOG.debug("computed sets of disjoint classes in ${Instant.now().toEpochMilli() - lastTimestamp}ms")

        val nonDisjointPairs: Set<Pair<OWLClass, OWLClass>> = nonDefinedClasses.flatMap { firstClass ->
            nonDefinedClasses
                // do not check classes that are sub-/superclasses of each other
                .filter { secondClass -> secondClass !in superClasses[firstClass]!! && firstClass !in superClasses[secondClass]!! }
                // filter out any disjoint classes
                .filter { secondClass -> secondClass !in disjointSets[firstClass]!! }
                .map { secondClass ->
                    Pair(
                        firstClass,
                        secondClass,
                    )
                }
        }.toSet()

        LOG.info(
            "found ${nonDisjointPairs.size} candidate pairs for missing disjointness axioms " +
                "in ${Instant.now().toEpochMilli() - startDetection}ms"
        )
        lastTimestamp = Instant.now().toEpochMilli()
        val startAggregation = lastTimestamp

        // aggregate to have a set of possible disjoint classes to each class
        val possibleDisjointClassesPerClass: Map<OWLClass, Set<OWLClass>> = nonDisjointPairs
            .map { (firstClass, _) -> firstClass }.associateWith { owlClass ->
                nonDisjointPairs
                    .mapNotNull { (first, second) ->
                        when (owlClass) {
                            first -> second
                            second -> first
                            else -> null
                        }
                    }
                    .toSet()
            }

        LOG.debug("aggregated to candidate sets for each class in ${Instant.now().toEpochMilli() - lastTimestamp}ms")
        lastTimestamp = Instant.now().toEpochMilli()

        // to reduce the candidates, compute common superclasses of the possibly disjoint classes of each class
        val topDisjointnessCandidatesPerClass: Map<OWLClass, Set<OWLClass>> = possibleDisjointClassesPerClass.entries
            .map { (owlClass, disjointnessCandidates) ->
                if (disjointnessCandidates.size == 1) {
                    // just pass the single-element set
                    owlClass to disjointnessCandidates
                } else {
                    val commonSuperClasses: Set<OWLClass> = disjointnessCandidates
                        .flatMapIndexed { outerIndex, candidate1 ->
                            disjointnessCandidates
                                // only process each combination once
                                .filterIndexed { innerIndex, _ -> outerIndex > innerIndex }
                                .flatMap { candidate2 ->
                                    val commonSuperClasses = superClasses[candidate1]!!
                                        .intersect(superClasses[candidate2]!!)
                                        .minus(superClasses[owlClass]!!)
                                        .filter { it !in disjointSets[owlClass]!! }

                                    return@flatMap if (commonSuperClasses.isNotEmpty()) {
                                        filterTopSuperClasses(superClasses, commonSuperClasses)
                                    } else {
                                        // if there are no common superclasses, the two candidates and their superclasses
                                        // remain top candidates themselves
                                        val candidates = superClasses[candidate1]!!.toMutableSet()
                                        candidates.addAll(superClasses[candidate2]!!)
                                        candidates.minus(superClasses[owlClass]!!)
                                        candidates
                                    }
                                }
                        }
                        .toSet()

                    // minimal set op top disjointness candidates for the class
                    owlClass to filterTopSuperClasses(superClasses, commonSuperClasses)
                }
            }
            .toMap()

        val reducedCandidatePairs = topDisjointnessCandidatesPerClass.entries
            .flatMap { (owlClass, topCandidates) ->
                topCandidates.map { topCandidate ->
                    // return Pair with first < second according to compareTo (to enable distinct afterwards)
                    if (owlClass < topCandidate) owlClass to topCandidate
                    else topCandidate to owlClass
                }
            }
            .distinct()

        LOG.debug("reduced candidate sets for each class in ${Instant.now().toEpochMilli() - lastTimestamp}ms")

        val minimalCandidatePairs = reducedCandidatePairs
            // we need to eliminate pairs for which a pair with the same class and a superclass of the other exists
            .filter { (first, second) ->
                reducedCandidatePairs.minus(first to second).none { (otherFirst, otherSecond) ->
                    when {
                        first == otherFirst -> otherSecond in superClasses[second]!!
                        first == otherSecond -> otherFirst in superClasses[second]!!
                        second == otherFirst -> otherSecond in superClasses[first]!!
                        second == otherSecond -> otherFirst in superClasses[first]!!
                        else -> false
                    }
                }
            }
            // also filter out any pairs that increase the count of unsat classes
            .filter { (first, second) ->
                val disjointnessAxiom = OWLDisjointClassesAxiomImpl(listOf(first, second), emptyList())
                val changeApplied = ontology.addAxiom(disjointnessAxiom)
                if (changeApplied != ChangeApplied.SUCCESSFULLY) {
                    LOG.error("could not apply change to ontology: disjointness between ${first.iri.toQuotedString()} and ${second.iri.toQuotedString()}")
                    return@filter false
                }
                if (reasoner.unsatisfiableClasses.size > unsatisfiableClasses.size) {
                    LOG.debug(
                        "increased number of unsat classes: disjointness between ${first.iri.toQuotedString()} and ${second.iri.toQuotedString()}," +
                            "now unsatisfiable: ${reasoner.unsatisfiableClasses.minus(unsatisfiableClasses)}"
                    )
                    // revert change
                    ontology.remove(disjointnessAxiom)
                    return@filter false
                }
                // revert change
                ontology.remove(disjointnessAxiom)
                return@filter true
            }

        LOG.info(
            "returning result, reduced number of candidate pairs from ${nonDisjointPairs.size} to ${minimalCandidatePairs.size}" +
                " in ${Instant.now().toEpochMilli() - startAggregation}ms"
        )

        return minimalCandidatePairs.map { (first, second) ->
            detectedCandidateMapper.owlClassesToDefectCandidate(ontology, listOf(first, second))
        }
    }

    /**
     * filter out all commonSuperclasses which have a superclass in the Collection (i.e. are not a top common superclass)
     */
    private fun filterTopSuperClasses(
        superClasses: Map<OWLClass, Set<OWLClass>>,
        commonSuperClasses: Collection<OWLClass>
    ): Set<OWLClass> {
        return commonSuperClasses
            .filter { commonSuperclass ->
                commonSuperClasses
                    .minus(commonSuperclass)
                    .none { it in superClasses[commonSuperclass]!! }
            }
            .toSet()
    }
}

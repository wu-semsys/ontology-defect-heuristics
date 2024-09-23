package at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.impl

import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.model.DefectCandidateDto
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.DefectDetectionHeuristic
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.mapper.DetectedCandidateMapper
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.semanticweb.owlapi.model.AxiomType
import org.semanticweb.owlapi.model.ClassExpressionType
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLObjectAllValuesFrom
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.reasoner.OWLReasoner
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.time.Instant

@Service
@Qualifier(TriviallySatisfiableAllValuesFromHeuristic.ERROR_TYPE_NAME)
class TriviallySatisfiableAllValuesFromHeuristic(
    private val detectedCandidateMapper: DetectedCandidateMapper,
) : DefectDetectionHeuristic {

    companion object {
        val LOG: Logger = LogManager.getLogger()
        const val ERROR_TYPE_NAME = "trivially-satisfiable-allValuesFrom"
    }

    override fun getErrorTypeName() = ERROR_TYPE_NAME

    override fun findCandidates(ontology: OWLOntology, reasoner: OWLReasoner): List<DefectCandidateDto> {
        val lastTimestamp = Instant.now().toEpochMilli()

        val classesWithTriviallySatisfiableAllValuesFromRestrictions = ontology.axioms().toList()
            .filter { it.axiomType in listOf(AxiomType.EQUIVALENT_CLASSES, AxiomType.SUBCLASS_OF) }
            .flatMap { axiom ->
                val owlComponent = if (axiom.axiomType == AxiomType.EQUIVALENT_CLASSES)
                    (axiom.componentsWithoutAnnotations().toList().first() as List<*>).first()
                else // (axiomType == AxiomType.SUBCLASS_OF)
                    axiom.componentsWithoutAnnotations().toList().first()

                axiom.nestedClassExpressions().toList()
                    .filter { it.classExpressionType == ClassExpressionType.OBJECT_ALL_VALUES_FROM }
                    .map { owlComponent as OWLClass to it }
            }
            // include all inferred subclasses, as they all have the allValuesFrom too
            .flatMap { (ontClass, allValuesFromRestriction) ->
                listOf(
                    listOf(ontClass),
                    reasoner.subClasses(ontClass, false).toList()
                ).flatten().map { subClass ->
                    subClass to allValuesFromRestriction
                }
            }
            .asSequence()
            .distinct()
            // filter out unsatisfiable classes
            .filter { (ontClass, _) -> reasoner.isSatisfiable(ontClass) }
            .map { (ontClass, allValuesFromRestriction) ->
                val someValuesFromRestrictions = listOf(
                    listOf(ontClass),
                    reasoner.superClasses(ontClass, false).toList()
                ).flatten()
                    .flatMap { superClass ->
                        ontology.axioms(superClass).toList()
                            .flatMap { axiom ->
                                axiom.nestedClassExpressions().toList()
                                    .filter { it.classExpressionType == ClassExpressionType.OBJECT_SOME_VALUES_FROM }
                                    .map { it as OWLObjectSomeValuesFrom }
                            }
                    }

                Triple(ontClass, allValuesFromRestriction, someValuesFromRestrictions)
            }
            .map { (ontClass, allValuesFromRestriction, someValuesFromRestrictions) ->
                Triple(
                    ontClass,
                    allValuesFromRestriction as OWLObjectAllValuesFrom,
                    someValuesFromRestrictions
                )
            }
            .mapNotNull { (ontClass, allValuesFrom, someValuesFromRestrictions) ->
                LOG.debug(
                    "checking $ontClass for someValuesFrom restrictions " +
                        "corresponding to the allValuesFrom restrictions $allValuesFrom, " +
                        "someValuesFrom to check: $someValuesFromRestrictions"
                )

                val classesProbablyCompatibleWithFiller = listOf(
                    allValuesFrom.filler.classesInSignature().toList(),
                    allValuesFrom.filler.classesInSignature().toList().flatMap { reasoner.superClasses(it).toList() },
                    allValuesFrom.filler.classesInSignature().toList().flatMap { reasoner.subClasses(it).toList() }
                ).flatten().distinct()

                val hasFittingSomeValues = someValuesFromRestrictions.any { someValuesFrom ->
                    // same property or subproperty of it
                    (
                        allValuesFrom.property == someValuesFrom.property ||
                            allValuesFrom.property in reasoner.superObjectProperties(someValuesFrom.property).toList()
                        ) &&
                        // filler is compatible
                        someValuesFrom.filler.classesInSignature().toList()
                            .any { it in classesProbablyCompatibleWithFiller }
                }

                if (!hasFittingSomeValues) {
                    ontClass to allValuesFrom
                } else {
                    null
                }
            }
            .onEach { LOG.debug("probably trivially satisfiable: ${it.first} with allValuesFrom: ${it.second}") }
            .toList()

        LOG.info(
            "found ${classesWithTriviallySatisfiableAllValuesFromRestrictions.size} classes " +
                "with trivially satisfiable allValuesFrom restrictions in ${Instant.now().toEpochMilli() - lastTimestamp}ms"
        )

        return classesWithTriviallySatisfiableAllValuesFromRestrictions
            .map { it.first }
            .map { detectedCandidateMapper.owlClassesToDefectCandidate(ontology, listOf(it)) }
    }
}

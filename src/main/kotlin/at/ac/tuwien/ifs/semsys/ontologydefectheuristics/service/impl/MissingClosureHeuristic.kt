package at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.impl

import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.model.DefectCandidateDto
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.util.Tuple4
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.DefectDetectionHeuristic
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.impl.CommonFunctions.isEquivalentClass
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.mapper.DetectedCandidateMapper
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.semanticweb.owlapi.model.AxiomType
import org.semanticweb.owlapi.model.ClassExpressionType
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLObjectAllValuesFrom
import org.semanticweb.owlapi.model.OWLObjectProperty
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.reasoner.OWLReasoner
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.time.Instant

@Service
@Qualifier(MissingClosureHeuristic.ERROR_TYPE_NAME)
class MissingClosureHeuristic(
    private val detectedCandidateMapper: DetectedCandidateMapper,
) : DefectDetectionHeuristic {

    companion object {
        val LOG: Logger = LogManager.getLogger()
        const val ERROR_TYPE_NAME = "missing-closure"
    }

    override fun getErrorTypeName() = ERROR_TYPE_NAME

    override fun findCandidates(ontology: OWLOntology, reasoner: OWLReasoner): List<DefectCandidateDto> {
        val lastTimestamp = Instant.now().toEpochMilli()

        val classesWithMissingClosureAxioms = ontology.axioms().toList()
            // we are only interested in subclasses, not equivalent classes
            .filter { it.axiomType in listOf(AxiomType.SUBCLASS_OF) }
            // retrieve classes and the properties they have someValuesFrom restrictions on
            .flatMap { axiom ->
                val owlComponent = axiom.componentsWithoutAnnotations().toList().first()

                axiom.nestedClassExpressions().toList()
                    .filter { it.classExpressionType == ClassExpressionType.OBJECT_SOME_VALUES_FROM }
                    .map { (owlComponent as OWLClass) to (it as OWLObjectSomeValuesFrom) }
                    .distinct()
            }
            .groupBy { (owlClass, someValuesFrom) -> owlClass to someValuesFrom.property }
            .entries.asSequence().map { (classAndProperty, classesAndSomeValuesFroms) ->
                Triple(
                    classAndProperty.first,
                    classAndProperty.second as OWLObjectProperty,
                    classesAndSomeValuesFroms.map { it.second }
                )
            }
            // filter out equivalent classes
            .filter { (owlClass, _, _) -> !isEquivalentClass(ontology, owlClass) }
            // filter out unsatisfiable classes
            .filter { (owlClass, _, _) -> reasoner.isSatisfiable(owlClass) }
            .map { (owlClass, property, someValuesFroms) ->
                // get allValuesFroms on the class and its superclasses
                val allValuesFroms = listOf(
                    listOf(owlClass),
                    reasoner.superClasses(owlClass, false).toList()
                ).flatten()
                    .flatMap { superClass ->
                        ontology.axioms(superClass).toList()
                            .flatMap { axiom ->
                                axiom.nestedClassExpressions().toList()
                                    .filter { it.classExpressionType == ClassExpressionType.OBJECT_ALL_VALUES_FROM }
                                    .map { it as OWLObjectAllValuesFrom }
                            }
                    }

                Tuple4(owlClass, property, someValuesFroms, allValuesFroms)
            }
            .filter { (_, property, _, allValuesFroms) ->
                // expand property of someValuesFrom to its set of superproperties (including itself)
                val possibleClosureProperties = listOf(
                    reasoner.superObjectProperties(property).toList(),
                    listOf(property)
                ).flatten()

                // check if any of the allValuesFroms is on a fitting property
                allValuesFroms
                    .none { allValuesFrom -> allValuesFrom.property in possibleClosureProperties }
            }
            // also filter out candidates on functional properties and all fillers being subclasses of the range
            .filterNot { (_, property, someValuesFroms, _) ->
                val rangeClasses = reasoner.getObjectPropertyRanges(property, true).entities().toList()
                val isFunctional = ontology.functionalObjectPropertyAxioms(property).toList().isNotEmpty()

                return@filterNot isFunctional &&
                    someValuesFroms.map { it.filler }.all { someValuesFromFiller ->
                        someValuesFromFiller in rangeClasses ||
                            rangeClasses.any { it in reasoner.superClasses(someValuesFromFiller).toList() }
                    }
            }
            .map { (classWithMissingClosure, _, _, _) ->
                detectedCandidateMapper.owlClassesToDefectCandidate(ontology, listOf(classWithMissingClosure))
            }.toList()

        LOG.info(
            "found ${classesWithMissingClosureAxioms.size} classes " +
                "with missing closure axioms in ${Instant.now().toEpochMilli() - lastTimestamp}ms"
        )

        return classesWithMissingClosureAxioms
    }
}

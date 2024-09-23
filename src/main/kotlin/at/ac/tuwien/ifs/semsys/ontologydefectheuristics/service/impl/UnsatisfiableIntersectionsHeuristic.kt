package at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.impl

import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.model.DefectCandidateDto
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.DefectDetectionHeuristic
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.mapper.DetectedCandidateMapper
import com.clarkparsia.owlapi.explanation.BlackBoxExplanation
import com.clarkparsia.owlapi.explanation.HSTExplanationGenerator
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.semanticweb.owlapi.model.ClassExpressionType
import org.semanticweb.owlapi.model.OWLClassExpression
import org.semanticweb.owlapi.model.OWLDataFactory
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.reasoner.OWLReasoner
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.time.Instant

@Service
@Qualifier(UnsatisfiableIntersectionsHeuristic.ERROR_TYPE_NAME)
class UnsatisfiableIntersectionsHeuristic(
    private val dataFactory: OWLDataFactory,
    private val reasonerFactory: OWLReasonerFactory,
    private val detectedCandidateMapper: DetectedCandidateMapper,
) : DefectDetectionHeuristic {

    companion object {
        val LOG: Logger = LogManager.getLogger()
        const val ERROR_TYPE_NAME = "unsatisfiable-intersections"
    }

    override fun getErrorTypeName() = ERROR_TYPE_NAME

    override fun findCandidates(ontology: OWLOntology, reasoner: OWLReasoner): List<DefectCandidateDto> {
        val firstTimestamp = Instant.now().toEpochMilli()
        var lastTimestamp = Instant.now().toEpochMilli()

        val unsatisfiableClasses = reasoner.unsatisfiableClasses.entities().toList()
            .filter { it != dataFactory.owlNothing }

        LOG.debug("time for retrieving unsatisfiable classes: ${Instant.now().toEpochMilli() - lastTimestamp}ms")
        lastTimestamp = Instant.now().toEpochMilli()

        val explanationGenerator = HSTExplanationGenerator(BlackBoxExplanation(ontology, reasonerFactory, reasoner))

        LOG.debug("time for instantiating explanation generator: ${Instant.now().toEpochMilli() - lastTimestamp}ms")
        lastTimestamp = Instant.now().toEpochMilli()

        val unsatIntersectionResult = unsatisfiableClasses
            .map { unsatisfiableClass ->
                val explanation = explanationGenerator.getExplanation(unsatisfiableClass)
                LOG.debug("explanation for ${unsatisfiableClass.iri.remainder.get()}: $explanation")
                return@map Pair(unsatisfiableClass, explanation)
            }
            .also {
                LOG.debug("time for retrieving explanation: ${Instant.now().toEpochMilli() - lastTimestamp}ms")
                lastTimestamp = Instant.now().toEpochMilli()
            }
            .filter { (_, explanation) ->
                val intersectionsContainedInExplanation = extractClassExpressionsOfType(
                    explanation.flatMap { axiom -> axiom.nestedClassExpressions().toList() }.toSet(),
                    ClassExpressionType.OBJECT_INTERSECTION_OF,
                    mutableSetOf()
                )

                intersectionsContainedInExplanation
                    .any { !reasoner.isSatisfiable(it) }
            }
            // map to result DTO type
            .map { (unsatClass, _) ->
                detectedCandidateMapper.owlClassesToDefectCandidate(ontology, listOf(unsatClass))
            }

        LOG.info("found ${unsatIntersectionResult.size} unsatisfiable classes with intersections in ${lastTimestamp - firstTimestamp}ms")

        return unsatIntersectionResult
    }

    private fun extractClassExpressionsOfType(
        expressions: Set<OWLClassExpression>,
        type: ClassExpressionType,
        result: MutableSet<OWLClassExpression>
    ): Set<OWLClassExpression> {
        // end of recursion
        if (expressions.isEmpty()) return result

        // add nested intersections
        result.addAll(
            expressions.filter { expression ->
                expression.classExpressionType == type
            }
        )

        // recurse into nested expressions
        return extractClassExpressionsOfType(
            expressions.flatMap { it.nestedClassExpressions().toList() }.toSet().minus(expressions),
            type,
            result
        )
    }
}

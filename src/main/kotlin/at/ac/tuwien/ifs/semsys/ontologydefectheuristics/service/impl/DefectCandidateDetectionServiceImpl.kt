package at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.impl

import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.model.ErrorTypeDto
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.DefectCandidateDetectionService
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.DefectDetectionHeuristic
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.ReasoningService
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.semanticweb.owlapi.model.OWLOntology
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class DefectCandidateDetectionServiceImpl(
    private val reasoningService: ReasoningService,
    private val heuristics: List<DefectDetectionHeuristic>
) : DefectCandidateDetectionService {

    companion object {
        val LOG: Logger = LogManager.getLogger()
    }

    override fun findCandidates(ontology: OWLOntology): List<ErrorTypeDto> {
        val startInitReasoner = Instant.now().toEpochMilli()
        val reasoner = reasoningService.initializeReasoner(ontology)
        LOG.info("initialized reasoner in ${Instant.now().toEpochMilli() - startInitReasoner}ms")
        return heuristics.map { heuristic ->
            ErrorTypeDto(
                heuristic.getErrorTypeName(),
                heuristic.findCandidates(ontology, reasoner)
            )
        }
    }
}

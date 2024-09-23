package at.ac.tuwien.ifs.semsys.ontologydefectheuristics.rest

import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.model.ErrorTypeDto
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.DefectCandidateDetectionService
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.OntologyParser
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
class DefectCandidateDetectionResource(
    private val defectCandidateDetectionService: DefectCandidateDetectionService,
    private val ontologyParser: OntologyParser,
) {

    companion object {
        val LOG: Logger = LogManager.getLogger()
    }

    @PostMapping("/defectCandidates")
    fun getDetectCandidates(@RequestBody source: String): List<ErrorTypeDto> {
        val requestTime = Instant.now().toEpochMilli()
        LOG.info("received request with ontology consisting of ${source.length} characters")
        LOG.debug("ontology starts with: ${source.substring(0, 100)}")
        val ontology = ontologyParser.parse(source)
        val result = defectCandidateDetectionService.findCandidates(ontology)
        LOG.info("completed request after ${Instant.now().toEpochMilli() - requestTime}ms")
        return result
    }
}

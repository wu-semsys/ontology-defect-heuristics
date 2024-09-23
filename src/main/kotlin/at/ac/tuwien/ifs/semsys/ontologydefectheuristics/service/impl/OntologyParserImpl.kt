package at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.impl

import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.OntologyParser
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.OWLOntology
import org.springframework.stereotype.Service
import java.time.Instant


@Service
class OntologyParserImpl : OntologyParser {

    companion object {
        val LOG: Logger = LogManager.getLogger()
    }

    override fun parse(source: String): OWLOntology {
        val startParsing = Instant.now().toEpochMilli()
        val parsedOntology = OWLManager.createOWLOntologyManager()
                .loadOntologyFromOntologyDocument(source.byteInputStream())
        LOG.info("parsed ontology in ${Instant.now().toEpochMilli() - startParsing}ms")
        return parsedOntology
    }
}

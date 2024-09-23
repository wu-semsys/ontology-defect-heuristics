package at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.impl

import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.ReasoningService
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.reasoner.OWLReasoner
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory
import org.springframework.stereotype.Service

@Service
class ReasoningServiceImpl(
    private val reasonerFactory: OWLReasonerFactory,
) : ReasoningService {

    override fun initializeReasoner(ontology: OWLOntology): OWLReasoner {
        val reasoner: OWLReasoner = reasonerFactory.createNonBufferingReasoner(ontology)

        reasoner.precomputableInferenceTypes()
            .forEach { reasoner.precomputeInferences(it) }

        return reasoner
    }
}

package at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service

import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.reasoner.OWLReasoner

interface ReasoningService {

    fun initializeReasoner(ontology: OWLOntology): OWLReasoner
}

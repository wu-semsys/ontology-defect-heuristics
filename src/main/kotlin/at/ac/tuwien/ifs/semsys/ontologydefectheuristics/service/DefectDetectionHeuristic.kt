package at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service

import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.model.DefectCandidateDto
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.reasoner.OWLReasoner

interface DefectDetectionHeuristic {

    fun getErrorTypeName(): String

    fun findCandidates(ontology: OWLOntology, reasoner: OWLReasoner): List<DefectCandidateDto>
}

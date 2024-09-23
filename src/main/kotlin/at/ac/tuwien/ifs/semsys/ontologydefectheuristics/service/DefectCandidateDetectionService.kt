package at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service

import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.model.ErrorTypeDto
import org.semanticweb.owlapi.model.OWLOntology

interface DefectCandidateDetectionService {

    fun findCandidates(ontology: OWLOntology): List<ErrorTypeDto>
}

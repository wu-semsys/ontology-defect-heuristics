package at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.mapper

import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.model.DefectCandidateDto
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLOntology

interface DetectedCandidateMapper {

    fun owlClassesToDefectCandidate(ontology: OWLOntology, classes: Collection<OWLClass>): DefectCandidateDto
}

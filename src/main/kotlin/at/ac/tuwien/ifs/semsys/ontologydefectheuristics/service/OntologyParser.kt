package at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service

import org.semanticweb.owlapi.model.OWLOntology

interface OntologyParser {

    fun parse(source: String): OWLOntology
}

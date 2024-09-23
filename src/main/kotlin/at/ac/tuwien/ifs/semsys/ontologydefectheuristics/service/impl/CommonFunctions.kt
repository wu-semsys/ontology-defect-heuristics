package at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.impl

import org.semanticweb.owlapi.model.AxiomType
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLOntology

object CommonFunctions {

    fun isEquivalentClass(ontology: OWLOntology, superClass: OWLClass) =
        ontology.axioms(superClass).toList().any { it.axiomType == AxiomType.EQUIVALENT_CLASSES }
}

package at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.mapper.impl

import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.model.ClassDescriptionDto
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.model.DefectCandidateDto
import at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.mapper.DetectedCandidateMapper
import org.semanticweb.owlapi.io.OWLObjectRenderer
import org.semanticweb.owlapi.model.AxiomType
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLDataFactory
import org.semanticweb.owlapi.model.OWLDisjointClassesAxiom
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.search.EntitySearcher
import org.springframework.stereotype.Component

@Component
class DetectedCandidateMapperImpl(
    private val dataFactory: OWLDataFactory,
    private val owlObjectRenderer: OWLObjectRenderer,
) : DetectedCandidateMapper {

    override fun owlClassesToDefectCandidate(ontology: OWLOntology, classes: Collection<OWLClass>): DefectCandidateDto =
        DefectCandidateDto(
            classes = classes.map { owlClass ->
                val label = owlClass.iri.remainder.orElse("<no label>")
                val disjointnessAxioms = ontology.axioms(owlClass).toList()
                    .filter { it.isOfType(AxiomType.DISJOINT_CLASSES) }

                ClassDescriptionDto(
                    label = label,
                    renderedAxioms =
                        listOf(
                            ontology.axioms(owlClass).toList()
                                .minus(disjointnessAxioms)
                                .map {
                                    owlObjectRenderer.render(it)
                                        .replace("\n", "")
                                },
                                if (disjointnessAxioms.isNotEmpty()) {
                                    disjointnessAxioms.joinToString(
                                        prefix = "$label DisjointWith (each of) ",
                                        separator = ", "
                                    ) { disjointnessAxiom ->
                                        (disjointnessAxiom as OWLDisjointClassesAxiom).operandsAsList
                                            .map { it.asOWLClass() }
                                            .find { it.iri != owlClass.iri }
                                            ?.iri?.remainder?.orElse(null)
                                            ?: error("could not extract other class from disjointness axiom")
                                    }.let { listOf(it) }
                                } else {
                                    emptyList()
                                }
                            ).flatten(),
                    context = EntitySearcher.getAnnotations(owlClass, ontology, dataFactory.rdfsComment).toList()
                        .filter { it.value.isLiteral }
                        .map { it.value.asLiteral().get().literal }
                        .firstOrNull()
                        ?.split("\", \"")
                        ?: emptyList()
                )
            }
        )
}

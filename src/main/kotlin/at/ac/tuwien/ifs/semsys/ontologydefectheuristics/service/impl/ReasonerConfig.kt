package at.ac.tuwien.ifs.semsys.ontologydefectheuristics.service.impl

import com.clarkparsia.owlapi.explanation.io.ConciseExplanationRenderer
import com.clarkparsia.owlapi.explanation.io.ExplanationRenderer
import org.semanticweb.HermiT.ReasonerFactory
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.io.OWLObjectRenderer
import org.semanticweb.owlapi.manchestersyntax.renderer.ManchesterOWLSyntaxOWLObjectRendererImpl
import org.semanticweb.owlapi.model.OWLDataFactory
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ReasonerConfig {

    @Bean
    fun owlObjectRenderer(): OWLObjectRenderer =
        // alternatives: DLSyntax (German/logic notation), Simple (notation of OWL definition)
        ManchesterOWLSyntaxOWLObjectRendererImpl()

    @Bean
    fun explanationRenderer(): ExplanationRenderer = ConciseExplanationRenderer()

    @Bean
    fun dataFactory(): OWLDataFactory = OWLManager.getOWLDataFactory()

    @Bean
    fun reasonerFactory(): OWLReasonerFactory = ReasonerFactory()
}

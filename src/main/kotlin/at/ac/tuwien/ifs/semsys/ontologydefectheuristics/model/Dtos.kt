package at.ac.tuwien.ifs.semsys.ontologydefectheuristics.model

data class DefectCandidateDto(
    val classes: List<ClassDescriptionDto>
)

data class ClassDescriptionDto(
    val label: String,
    val renderedAxioms: List<String>,
    val context: List<String>,
)

data class ErrorTypeDto(
    val name: String,
    val candidates: List<DefectCandidateDto>
)

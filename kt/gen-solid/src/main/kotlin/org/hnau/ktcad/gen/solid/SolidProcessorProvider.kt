package org.hnau.ktcad.gen.solid

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class SolidProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        SolidProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
}

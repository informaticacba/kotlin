/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.export

import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.JsAstUtils
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.defineProperty
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.jsAssignment
import org.jetbrains.kotlin.ir.backend.js.utils.IrNamer
import org.jetbrains.kotlin.ir.backend.js.utils.Namer
import org.jetbrains.kotlin.js.backend.ast.*

class ExportModelToJsStatements(
    private val namer: IrNamer,
    private val declareNewNamespace: (String) -> String
) {
    private val namespaceToRefMap = mutableMapOf<String, JsNameRef>()

    fun generateModuleExport(module: ExportedModule, internalModuleName: JsName): List<JsStatement> {
        return module.declarations.flatMap { generateDeclarationExport(it, JsNameRef(internalModuleName), esModules = false) }
    }

    fun generateDeclarationExport(
        declaration: ExportedDeclaration,
        namespace: JsNameRef?,
        esModules: Boolean
    ): List<JsStatement> {
        return when (declaration) {
            is ExportedNamespace -> {
                require(namespace != null) { "Only namespaced namespaces are allowed" }
                val statements = mutableListOf<JsStatement>()
                val elements = declaration.name.split(".")
                var currentNamespace = ""
                var currentRef: JsNameRef = namespace
                for (element in elements) {
                    val newNamespace = "$currentNamespace$$element"
                    val newNameSpaceRef = namespaceToRefMap.getOrPut(newNamespace) {
                        val varName = JsName(declareNewNamespace(newNamespace), false)
                        val namespaceRef = JsNameRef(element, currentRef)
                        statements += JsVars(
                            JsVars.JsVar(
                                varName,
                                JsAstUtils.or(
                                    namespaceRef,
                                    jsAssignment(
                                        namespaceRef,
                                        JsObjectLiteral()
                                    )
                                )
                            )
                        )
                        JsNameRef(varName)
                    }
                    currentRef = newNameSpaceRef
                    currentNamespace = newNamespace
                }
                statements + declaration.declarations.flatMap { generateDeclarationExport(it, currentRef, esModules) }
            }

            is ExportedFunction -> {
                val name = namer.getNameForStaticDeclaration(declaration.ir)
                if (esModules) {
                    listOf(JsExport(name, alias = JsName(declaration.name, false)))
                } else {
                    if (namespace != null) {
                        listOf(
                            jsAssignment(
                                JsNameRef(declaration.name, namespace),
                                JsNameRef(name)
                            ).makeStmt()
                        )
                    } else emptyList()
                }
            }

            is ExportedConstructor -> emptyList()

            is ExportedProperty -> {
                require(namespace != null) { "Only namespaced properties are allowed" }
                val underlying: List<JsStatement> = declaration.exportedObject?.let {
                    generateDeclarationExport(it, null, esModules)
                } ?: emptyList()
                val getter = declaration.irGetter?.let { JsNameRef(namer.getNameForStaticDeclaration(it)) }
                val setter = declaration.irSetter?.let { JsNameRef(namer.getNameForStaticDeclaration(it)) }
                listOf(defineProperty(namespace, declaration.name, getter, setter).makeStmt()) + underlying
            }

            is ErrorDeclaration -> emptyList()

            is ExportedClass -> {
                if (declaration.isInterface) return emptyList()
                val newNameSpace = if (namespace != null)
                    JsNameRef(declaration.name, namespace)
                else
                    JsNameRef(Namer.PROTOTYPE_NAME, declaration.name)
                val name = namer.getNameForStaticDeclaration(declaration.ir)
                val klassExport =
                    if (esModules) {
                        JsExport(name, alias = JsName(declaration.name, false))
                    } else {
                        if (namespace != null) {
                            jsAssignment(
                                newNameSpace,
                                JsNameRef(name)
                            ).makeStmt()
                        } else null
                    }

                // These are only used when exporting secondary constructors annotated with @JsName
                val staticFunctions = declaration.members.filter { it is ExportedFunction && it.isStatic }

                // Nested objects are exported as static properties
                val staticProperties = declaration.members.mapNotNull {
                    (it as? ExportedProperty)?.takeIf { it.isStatic }
                }

                val staticsExport = (staticFunctions + staticProperties + declaration.nestedClasses)
                    .flatMap { generateDeclarationExport(it, newNameSpace, esModules) }

                (klassExport?.let { listOf(it) } ?: emptyList()) + staticsExport
            }
        }
    }
}

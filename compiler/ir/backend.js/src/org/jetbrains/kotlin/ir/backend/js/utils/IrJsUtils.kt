package org.jetbrains.kotlin.ir.backend.js.utils

import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.util.parentClassOrNull

fun IrDeclaration?.isExportedInterface() =
    this is IrClass && kind.isInterface && isJsExport()

fun IrDeclaration.isExportedInterfaceMember() =
    parentClassOrNull.isExportedInterface()
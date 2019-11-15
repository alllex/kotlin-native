/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */
package org.jetbrains.kotlin.native.interop.gen

import kotlinx.metadata.*
import kotlinx.metadata.klib.*
import org.jetbrains.kotlin.backend.common.serialization.cityHash64
import org.jetbrains.kotlin.utils.addIfNotNull

class StubIrMetadataEmitter(
        private val context: StubIrContext,
        private val builderResult: StubIrBuilderResult,
        private val moduleName: String
) {
    fun emit(): KlibModuleMetadata {
        val annotations = emptyList<KmAnnotation>()
        val fragments = emitModuleFragments()
        return KlibModuleMetadata(moduleName, fragments, annotations)
    }

    private fun emitModuleFragments(): List<KmModuleFragment> =
        ModuleMetadataEmitter(context.validPackageName).let {
            builderResult.stubs.accept(it, null)
            listOf(it.writeModule())
        }
}

private interface StubIrToMetadataTranslationExtensions {

    val FunctionStub.flags: Flags
        get() = listOfNotNull(
                Flag.Common.IS_PUBLIC,
                Flag.Function.IS_EXTERNAL,
                Flag.HAS_ANNOTATIONS.takeIf { annotations.isNotEmpty() }
        ).let { flagsOf(*it.toTypedArray()) }

    val Classifier.fqNameSerialized: String
        get() = fqName.replace('.', '/')

    val PropertyStub.flags: Flags
        get() = listOfNotNull(
                Flag.IS_PUBLIC,
                Flag.Property.IS_DECLARATION,
                Flag.IS_FINAL,
                when (kind) {
                    is PropertyStub.Kind.Val -> null
                    is PropertyStub.Kind.Var -> Flag.Property.IS_VAR
                    is PropertyStub.Kind.Constant -> Flag.Property.IS_CONST
                },
                when (kind) {
                    is PropertyStub.Kind.Constant -> null
                    is PropertyStub.Kind.Val,
                    is PropertyStub.Kind.Var -> Flag.Property.HAS_GETTER
                },
                when (kind) {
                    is PropertyStub.Kind.Constant -> null
                    is PropertyStub.Kind.Val -> null
                    is PropertyStub.Kind.Var -> Flag.Property.HAS_SETTER
                }
        ).let { flagsOf(*it.toTypedArray()) }

    val PropertyStub.getterFlags: Flags
        get() = listOfNotNull(
                Flag.HAS_ANNOTATIONS.takeIf { annotations.isNotEmpty() },
                Flag.IS_PUBLIC,
                Flag.IS_FINAL,
                Flag.PropertyAccessor.IS_EXTERNAL
        ).let { flagsOf(*it.toTypedArray()) }

    val PropertyStub.setterFlags: Flags
        get() = listOfNotNull(
                Flag.HAS_ANNOTATIONS.takeIf { annotations.isNotEmpty() },
                Flag.IS_PUBLIC,
                Flag.IS_FINAL,
                Flag.PropertyAccessor.IS_EXTERNAL
        ).let { flagsOf(*it.toTypedArray()) }

    val StubType.flags: Flags
        get() = listOfNotNull(
                Flag.Type.IS_NULLABLE.takeIf { nullable }
        ).let { flagsOf(*it.toTypedArray()) }

    val TypealiasStub.flags: Flags
        get() = listOfNotNull(
                Flag.IS_PUBLIC
        ).let { flagsOf(*it.toTypedArray()) }

    fun AnnotationStub.map(): KmAnnotation {
        val args = when (this) {
            AnnotationStub.ObjC.ConsumesReceiver -> TODO()
            AnnotationStub.ObjC.ReturnsRetained -> TODO()
            is AnnotationStub.ObjC.Method -> TODO()
            is AnnotationStub.ObjC.Factory -> TODO()
            AnnotationStub.ObjC.Consumed -> TODO()
            is AnnotationStub.ObjC.Constructor -> TODO()
            is AnnotationStub.ObjC.ExternalClass -> TODO()
            AnnotationStub.CCall.CString -> TODO()
            AnnotationStub.CCall.WCString -> TODO()
            is AnnotationStub.CCall.Symbol ->
                mapOf("id" to KmAnnotationArgument.StringValue(symbolName))
            is AnnotationStub.CStruct -> TODO()
            is AnnotationStub.CNaturalStruct -> TODO()
            is AnnotationStub.CLength -> TODO()
            is AnnotationStub.Deprecated -> TODO()
        }
        return KmAnnotation(classifier.fqNameSerialized, args)
    }

    fun StubType.map(): KmType {
        return when (this) {
            is ClassifierStubType -> KmType(flags).also { km ->
                km.arguments += typeArguments.map { it.map() }
                if (isTypealias) {
                    km.abbreviatedType = abbreviatedType
                    km.classifier = expandedType.map().classifier
                } else {
                    km.classifier = KmClassifier.Class(classifier.fqNameSerialized)
                }

            }
            is FunctionalType -> KmType(flags).also { km ->
                km.classifier = KmClassifier.Class(classifier.fqNameSerialized)
            }
            is TypeParameterType -> KmType(flags).also { km ->
                km.classifier = KmClassifier.TypeParameter(id)
            }
        }
    }

    private val ClassifierStubType.abbreviatedType: KmType
        get() = KmType(flags).also { km ->
            km.classifier = KmClassifier.TypeAlias(classifier.fqNameSerialized)
        }

    fun FunctionParameterStub.map(): KmValueParameter =
            KmValueParameter(flags, name).also { km ->
                type.map().let {
                    if (isVararg) {
                        km.varargElementType = it
                    } else {
                        km.type = it
                    }
                }
            }

    fun TypeParameterStub.map(): KmTypeParameter =
            KmTypeParameter(flagsOf(), name, id, KmVariance.INVARIANT).also { km ->
                km.upperBounds.addIfNotNull(upperBound?.map())
            }

    private fun TypeArgument.map(): KmTypeProjection = when (this) {
        TypeArgument.StarProjection -> KmTypeProjection.STAR
        is TypeArgumentStub -> KmTypeProjection(variance.map(), type.map())
        else -> error("Unexpected TypeArgument: $this")
    }

    private fun TypeArgument.Variance.map(): KmVariance = when (this) {
        TypeArgument.Variance.INVARIANT -> KmVariance.INVARIANT
        TypeArgument.Variance.IN -> KmVariance.IN
        TypeArgument.Variance.OUT -> KmVariance.OUT
    }

    fun ConstantStub.map(): KmAnnotationArgument<*> = when (this) {
        is StringConstantStub -> KmAnnotationArgument.StringValue(value)
        is IntegralConstantStub -> when (size) {
            1 -> if (isSigned) {
                KmAnnotationArgument.ByteValue(value.toByte())
            } else {
                KmAnnotationArgument.UByteValue(value.toByte())
            }
            2 -> if (isSigned) {
                KmAnnotationArgument.ShortValue(value.toShort())
            } else {
                KmAnnotationArgument.UShortValue(value.toShort())
            }
            4 -> if (isSigned) {
                KmAnnotationArgument.IntValue(value.toInt())
            } else {
                KmAnnotationArgument.UIntValue(value.toInt())
            }
            8 -> if (isSigned) {
                KmAnnotationArgument.LongValue(value)
            } else {
                KmAnnotationArgument.ULongValue(value)
            }

            else -> error("Integral constant of value $value with unexpected size of $size.")
        }
        is DoubleConstantStub -> when (size) {
            4 -> KmAnnotationArgument.FloatValue(value.toFloat())
            8 -> KmAnnotationArgument.DoubleValue(value)
            else -> error("Floating-point constant of value $value with unexpected size of $size.")
        }
    }

    private val TypeParameterType.id: Int
        get() = TODO()

    private val TypeParameterStub.id: Int
        get() = TODO()

    private val FunctionParameterStub.flags: Flags
        get() = flagsOf()
}

private class StubIrUniqIdProvider {
    private val mangler = KotlinLikeInteropMangler()

    fun uniqIdForFunction(function: FunctionStub): UniqId = with(mangler) {
        when (function.origin) {
            is StubOrigin.Function -> function.origin.function.uniqueSymbolName
            is StubOrigin.ObjCMethod -> {
                require(this.context is ManglingContext.Entity) {
                    "Unexpected mangling context $context for method ${function.name}."
                }
                function.origin.method.uniqueSymbolName
            }
            // TODO: What to do with "create" method in Objective-C categories?
            else -> error("Unexpected origin ${function.origin} for function ${function.name}.")
        }.toUniqId()
    }

    fun uniqIdForProperty(property: PropertyStub): UniqId = with (mangler) {
        when (property.origin) {
            is StubOrigin.ObjCProperty -> {
                require(this.context is ManglingContext.Entity) {
                    "Unexpected mangling context $context for property ${property.name}."
                }
                property.origin.property.uniqueSymbolName
            }
            is StubOrigin.Constant -> property.origin.constantDef.uniqueSymbolName
            is StubOrigin.Global -> property.origin.global.uniqueSymbolName
            // TODO: What to do with origin for enum entries and struct fields?
            else -> error("Unexpected origin ${property.origin} for property ${property.name}.")
        }.toUniqId()
    }

    fun uniqIdForTypeAlias(typeAlias: TypealiasStub): UniqId = with (mangler) {
        when (typeAlias.origin) {
            is StubOrigin.TypeDef -> typeAlias.origin.typedefDef.uniqueSymbolName
            is StubOrigin.Enum -> typeAlias.origin.enum.uniqueSymbolName
            else -> error("Unexpected origin ${typeAlias.origin} for typealias ${typeAlias.alias.fqName}.")
        }
    }.toUniqId()

    private fun String.toUniqId() = UniqId(cityHash64())
}

/**
 * Translates single [StubContainer] to [KmModuleFragment].
 */
internal class ModuleMetadataEmitter(
        private val packageFqName: String
) : StubIrToMetadataTranslationExtensions, StubIrVisitor<StubContainer?, Unit> {

    private val uniqIds = StubIrUniqIdProvider()

    private val classes = mutableListOf<KmClass>()
    private val properties = mutableListOf<KmProperty>()
    private val typeAliases = mutableListOf<KmTypeAlias>()
    private val functions = mutableListOf<KmFunction>()

    fun writeModule(): KmModuleFragment = KmModuleFragment().also { km ->
        km.fqName = packageFqName
        km.classes.addAll(classes)
        km.pkg = writePackage()
    }

    private fun writePackage() = KmPackage().also { km ->
        km.fqName = packageFqName
        km.typeAliases.addAll(typeAliases)
        km.properties.addAll(properties)
        km.functions.addAll(functions)
    }

    override fun visitClass(element: ClassStub, data: StubContainer?) {
        // TODO("not implemented")
    }

    override fun visitTypealias(element: TypealiasStub, data: StubContainer?) {
        KmTypeAlias(element.flags, element.alias.topLevelName).apply {
            uniqId = uniqIds.uniqIdForTypeAlias(element)
            underlyingType = element.aliasee.map()
            expandedType = element.aliasee.expandedType.map()
        }.let(typeAliases::add)
    }

    override fun visitFunction(element: FunctionStub, data: StubContainer?) {
        KmFunction(element.flags, element.name).apply {
            annotations.addAll(element.annotations.map { it.map() })
            returnType = element.returnType.map()
            valueParameters.addAll(element.parameters.map { it.map() })
            typeParameters.addAll(element.typeParameters.map { it.map() })
            uniqId = uniqIds.uniqIdForFunction(element)
        }.let(functions::add)
    }

    override fun visitProperty(element: PropertyStub, data: StubContainer?) {
        KmProperty(element.flags, element.name, element.getterFlags, element.setterFlags).apply {
            annotations.addAll(element.annotations.map { it.map() })
            uniqId = uniqIds.uniqIdForProperty(element)
            returnType = element.type.map()
            if (element.kind is PropertyStub.Kind.Var) {
                val setter = element.kind.setter
                setterAnnotations += setter.annotations.map { it.map() }
                // TODO: Maybe it's better to explicitly add setter parameter in stub.
                setterParameter = FunctionParameterStub("value", element.type).map()
            }
            getterAnnotations += when (element.kind) {
                is PropertyStub.Kind.Val -> element.kind.getter.annotations.map { it.map() }
                is PropertyStub.Kind.Var -> element.kind.getter.annotations.map { it.map() }
                is PropertyStub.Kind.Constant -> emptyList()
            }
            if (element.kind is PropertyStub.Kind.Constant) {
                compileTimeValue = element.kind.constant.map()
            }
        }.let(properties::add)
    }

    override fun visitConstructor(constructorStub: ConstructorStub, data: StubContainer?) {
        // TODO("not implemented")
    }

    override fun visitPropertyAccessor(propertyAccessor: PropertyAccessor, data: StubContainer?) {
        // TODO("not implemented")
    }

    override fun visitSimpleStubContainer(simpleStubContainer: SimpleStubContainer, data: StubContainer?) {
        simpleStubContainer.children.forEach { it.accept(this, simpleStubContainer) }
    }
}
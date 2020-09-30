package lombok.javac.handlers;

import static lombok.javac.Javac.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;
import static lombok.javac.handlers.JavacHandlerUtil.MemberExistsResult.NOT_EXISTS;

import org.mangosdk.spi.ProviderFor;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

import lombok.AccessLevel;
import lombok.WithCodeAndDesc;
import lombok.core.AnnotationValues;
import lombok.core.LombokImmutableList;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import lombok.javac.handlers.HandleConstructor.SkipIfConstructorExists;
import lombok.javac.handlers.JavacHandlerUtil.MemberExistsResult;

@ProviderFor(JavacAnnotationHandler.class) public class HandleWithCodeAndDesc extends JavacAnnotationHandler<WithCodeAndDesc> {
	private HandleConstructor handleConstructor = new HandleConstructor();
	private HandleGetter handleGetter = new HandleGetter();
	
	@Override public void handle(AnnotationValues<WithCodeAndDesc> annotation, JCAnnotation ast, JavacNode annotationNode) {
		deleteAnnotationIfNeccessary(annotationNode, WithCodeAndDesc.class);
		JavacNode typeNode = annotationNode.up();
		if (!isClassOrEnum(typeNode)) {
			annotationNode.addError("@WithCodeAndDesc is only supported on an enum.");
			return;
		}
		
		boolean notAEnum = true;
		if (typeNode.get() instanceof JCTree.JCClassDecl) {
			long flags = ((JCTree.JCClassDecl) typeNode.get()).mods.flags;
			notAEnum = (flags & (Flags.INTERFACE | Flags.ANNOTATION)) != 0;
		}
		if (notAEnum) {
			annotationNode.addError("@WithCodeAndDesc is only supported on an enum.");
			return;
		}
		
		JavacTreeMaker treeMaker = typeNode.getTreeMaker();
		addCodeVar(annotation, typeNode, treeMaker, annotationNode);
		addDescVar(annotation, typeNode, treeMaker, annotationNode);
		addSingleCodeConstructor(annotation, typeNode, treeMaker, annotationNode);
		addCodeAndDescConstructor(annotation, typeNode, treeMaker, annotationNode);
		handleGetter.generateGetterForType(typeNode, annotationNode, AccessLevel.PUBLIC, true, List.<JCAnnotation>nil());
		
		if (methodExists("of", typeNode, -1) != MemberExistsResult.NOT_EXISTS) {
			annotationNode.addWarning("Method 'of' already exists.");
		} else {
			addOfMethod(annotation, typeNode, treeMaker, annotationNode);
		}
	}
	
	private void addCodeVar(AnnotationValues<WithCodeAndDesc> annotation, JavacNode typeNode, JavacTreeMaker treeMaker, JavacNode holderClass) {
		String varName = this.getAnnotatedParam(annotation, "codeName", "code");
		if (!NOT_EXISTS.equals(fieldExists(varName, typeNode))) {
			return;
		}
		
		JCTree.JCModifiers fieldMod = treeMaker.Modifiers(Flags.PRIVATE);
		JCExpression codeType = genJavaLangTypeRef(typeNode, "Integer");
		JCTree.JCVariableDecl codeVar = treeMaker.VarDef(fieldMod, typeNode.toName(varName), codeType, null);
		JavacHandlerUtil.injectField(typeNode, codeVar);
	}
	
	private void addDescVar(AnnotationValues<WithCodeAndDesc> annotation, JavacNode typeNode, JavacTreeMaker treeMaker, JavacNode holderClass) {
		String varName = this.getAnnotatedParam(annotation, "descName", "desc");
		if (!NOT_EXISTS.equals(fieldExists(varName, typeNode))) {
			return;
		}
		
		JCTree.JCModifiers fieldMod = treeMaker.Modifiers(Flags.PRIVATE);
		JCExpression descType = genJavaLangTypeRef(typeNode, "String");
		JCTree.JCVariableDecl descVar = treeMaker.VarDef(fieldMod, typeNode.toName(varName), descType, null);
		JavacHandlerUtil.injectField(typeNode, descVar);
	}
	
	private void addSingleCodeConstructor(AnnotationValues<WithCodeAndDesc> annotation, JavacNode typeNode, JavacTreeMaker maker, JavacNode holderClass) {
		String codeName = this.getAnnotatedParam(annotation, "codeName", "code");
		JavacNode codeField = getField(typeNode, codeName);
		handleConstructor.generateConstructor(typeNode, AccessLevel.PRIVATE, List.<JCAnnotation>nil(), List.of(codeField), false, "", SkipIfConstructorExists.NO, holderClass);
	}
	
	private void addCodeAndDescConstructor(AnnotationValues<WithCodeAndDesc> annotation, JavacNode typeNode, JavacTreeMaker maker, JavacNode holderClass) {
		String codeName = this.getAnnotatedParam(annotation, "codeName", "code");
		JavacNode codeField = getField(typeNode, codeName);
		String descName = this.getAnnotatedParam(annotation, "descName", "desc");
		JavacNode descField = getField(typeNode, descName);
		handleConstructor.generateConstructor(typeNode, AccessLevel.PRIVATE, List.<JCAnnotation>nil(), List.of(codeField, descField), false, "", SkipIfConstructorExists.NO, holderClass);
	}
	
	private void addOfMethod(AnnotationValues<WithCodeAndDesc> annotation, JavacNode typeNode, JavacTreeMaker maker, JavacNode holderClass) {
		JCTree.JCModifiers mods = maker.Modifiers(Flags.PUBLIC | Flags.STATIC);
		JCTree.JCClassDecl type = (JCTree.JCClassDecl) typeNode.get();
		JCTree.JCExpression returnType = maker.Ident(type.name);
		
		String codeFieldName = this.getAnnotatedParam(annotation, "codeName", "code");
		Name codeName = typeNode.toName(codeFieldName);
		JCExpression codeType = genJavaLangTypeRef(typeNode, "Integer");
		long flags = addFinalIfNeeded(Flags.PARAMETER, typeNode.getContext());
		JCVariableDecl param = maker.VarDef(maker.Modifiers(flags), codeName, codeType, null);
			
		JCStatement foreachStatement = this.createForeachStatement(annotation, typeNode, maker);
		JCStatement throwStatement = this.createThrowStatement(typeNode, maker);
		JCBlock block = maker.Block(0L, List.of(foreachStatement, throwStatement));
		JCTree.JCMethodDecl methodDef = maker.MethodDef(mods, typeNode.toName("of"), returnType, List.<JCTree.JCTypeParameter>nil(), List.<JCTree.JCVariableDecl>of(param), List.<JCTree.JCExpression>nil(), block, null);
		methodDef = recursiveSetGeneratedBy(methodDef, typeNode.get(), typeNode.getContext());
		JavacHandlerUtil.injectMethod(typeNode, methodDef);
	}
	
	/*
	 * for (SampleEnum entry: SampleEnum.values()) {
	 * 	if(entry.getCode().equals(code)) { return entry; } 
	 * }
	 */
	private JCStatement createForeachStatement(AnnotationValues<WithCodeAndDesc> annotation, JavacNode typeNode, JavacTreeMaker maker) {
		String codeName = this.getAnnotatedParam(annotation, "codeName", "code");
		List<JCExpression> jceBlank = List.nil();
		long baseFlags = JavacHandlerUtil.addFinalIfNeeded(0, typeNode.getContext());
		Name entryName = typeNode.toName("entry");
		JCTree.JCClassDecl type = (JCTree.JCClassDecl) typeNode.get();
        JCTree.JCExpression forEachType = maker.Ident(type.name);
        JCVariableDecl varDef = maker.VarDef(maker.Modifiers(baseFlags), entryName, forEachType, null);
		
		JCExpression entrySetInvocation = maker.Apply(jceBlank, maker.Select(maker.Ident(type.name), typeNode.toName("values")), jceBlank);
		JCTree.JCExpression entryExp = maker.Ident(typeNode.toName(codeName));
		JCExpression entryCodeMatch = maker.Apply(jceBlank, maker.Select( chainDots(typeNode, "entry", codeName), typeNode.toName("equals")), List.of(entryExp));
		JCStatement returnEntry = maker.Return(maker.Ident(entryName));
		JCStatement ifStatement = maker.If(entryCodeMatch, returnEntry, null);
		
		JCBlock forEachBody = maker.Block(0, List.of(ifStatement));
		return maker.ForeachLoop(varDef, entrySetInvocation, forEachBody);
	}
	
	// throw new IllegalArgumentException("Unknown code value, please check again");
	private JCStatement createThrowStatement(JavacNode typeNode, JavacTreeMaker maker) {
		JCExpression exceptionType = genJavaLangTypeRef(typeNode, "IllegalArgumentException");
		List<JCExpression> jceBlank = List.nil();
		JCExpression message = maker.Literal("Unknown code value, please check again");
		JCExpression exceptionInstance = maker.NewClass(null, jceBlank, exceptionType, List.of(message), null);
		JCStatement throwStatement = maker.Throw(exceptionInstance);
		return throwStatement;
	}
	
	private JavacNode getField(JavacNode typeNode, String fieldName) {
		LombokImmutableList<JavacNode> fields = typeNode.down();
		for (JavacNode field : fields) {
			if (field.getKind().equals(lombok.core.AST.Kind.FIELD) && field.getName().equals(fieldName)) {
				return field;
			}
		}
		return null;
	}
	
	private String getAnnotatedParam(AnnotationValues<WithCodeAndDesc> annotation, String paramName, String defaultValue) {
		String val = annotation.getAsString(paramName);
		if (val == null || val.trim().length() == 0) {
			val = annotation.getDefaultIf(paramName, String.class, defaultValue);
		}
		return val;
	}
}

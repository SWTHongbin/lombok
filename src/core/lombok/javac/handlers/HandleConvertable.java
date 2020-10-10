package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import lombok.Convertable;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;

import org.mangosdk.spi.ProviderFor;

import static lombok.javac.handlers.JavacHandlerUtil.*;

/**
 * Handles the {@code lombok.Convertable} annotation for javac.
 * <p>
 * public AnotherPojo toBean(); public static Pojo fromBean(AnotherPojo bean)
 */

@ProviderFor(JavacAnnotationHandler.class) 
public class HandleConvertable extends JavacAnnotationHandler<Convertable> {
	private static final String TO_BEAN_METHOD_NAME = "toBean", 
		FROM_BEAN_METHOD_NAME = "fromBean", 
		POJO_PARAM_NAME = "pojo", 
		BEAN_ANNOTATION_NAME = "bean", 
		CLAZZ_PARAM_NAME = "clazz",
		CONVERT_METHOD = "com.xyz.utils.JsonUtils.convert";
	
	@Override public void handle(AnnotationValues<Convertable> annotation, JCAnnotation ast, JavacNode annotationNode) {
		deleteAnnotationIfNeccessary(annotationNode, Convertable.class);
		JavacNode typeNode = annotationNode.up();
		if (!isClass(typeNode)) {
			annotationNode.addError("@Convertable is only supported on a class.");
			return;
		}
		
		boolean notAClass = true;
		if (typeNode.get() instanceof JCTree.JCClassDecl) {
			long flags = ((JCTree.JCClassDecl) typeNode.get()).mods.flags;
			notAClass = (flags & (Flags.INTERFACE | Flags.ANNOTATION)) != 0;
		}
		
		if (notAClass) {
			annotationNode.addError("@Convertable is only supported on a class.");
			return;
		}
		
		if (methodExists(TO_BEAN_METHOD_NAME, typeNode, -1) != MemberExistsResult.NOT_EXISTS) {
			annotationNode.addWarning("Method '" + TO_BEAN_METHOD_NAME + "' already exists.");
		} else {
			JCTree.JCMethodDecl method = createToBeanMethod(annotation, typeNode, annotationNode);
			injectMethod(typeNode, method);
		}
		if (methodExists(FROM_BEAN_METHOD_NAME, typeNode, 1) != MemberExistsResult.NOT_EXISTS) {
			annotationNode.addWarning("Method '" + FROM_BEAN_METHOD_NAME + "' already exists.");
		} else {
			JCTree.JCMethodDecl method = createFromBeanMethod(annotation, typeNode, annotationNode);
			injectMethod(typeNode, method);
		}
	}
	
	private JCTree.JCMethodDecl createToBeanMethod(AnnotationValues<Convertable> annotation, JavacNode typeNode, JavacNode annotationNode) {
		if (isBeanClassProvided(annotation)) {
			return createToBeanMethodWithBeanClass(annotation, typeNode, annotationNode);
		} else {
			return createToBeanMethodWithGeneric(annotation, typeNode, annotationNode);
		}
	}
	
	private JCTree.JCMethodDecl createToBeanMethodWithBeanClass(AnnotationValues<Convertable> annotation, JavacNode typeNode, JavacNode annotationNode) {
		JavacTreeMaker maker = typeNode.getTreeMaker();
		JCTree.JCModifiers mods = maker.Modifiers(Flags.PUBLIC);
		JCTree.JCExpression retrunType = getAnnotatedClassType(annotation, maker, typeNode);
		JCTree.JCStatement block = buildToBeanJcStatementWithBeanClass(retrunType, maker, typeNode);
		
		JCTree.JCMethodDecl methodDef = maker.MethodDef(mods, 
			typeNode.toName(TO_BEAN_METHOD_NAME),
			retrunType, 
			List.<JCTree.JCTypeParameter>nil(), 
			List.<JCTree.JCVariableDecl>nil(), 
			List.<JCTree.JCExpression>nil(), 
			maker.Block(0, List.of(block)), 
			null);
		return recursiveSetGeneratedBy(methodDef, annotationNode.get(), typeNode.getContext());
	}
	
	private JCTree.JCMethodDecl createToBeanMethodWithGeneric(AnnotationValues<Convertable> annotation, JavacNode typeNode, JavacNode annotationNode) {
		lombok.javac.JavacTreeMaker maker = typeNode.getTreeMaker();
		JCTree.JCModifiers mods = maker.Modifiers(Flags.PUBLIC);
		JCTree.JCStatement statements = buildToBeanJcStatementWithGeneric(typeNode, maker);
		JCTree.JCVariableDecl jcVariableDecl = buildToBeanParam(typeNode, maker);
		JCTree.JCTypeParameter typaram = maker.TypeParameter(typeNode.toName("T"), List.<JCTree.JCExpression>nil());
		
		JCTree.JCMethodDecl methodDef = maker.MethodDef(mods, 
			typeNode.toName(TO_BEAN_METHOD_NAME), 
			maker.Ident(typeNode.toName("T")), 
			List.<JCTree.JCTypeParameter>of(typaram), 
			List.<JCTree.JCVariableDecl>of(jcVariableDecl), 
			List.<JCTree.JCExpression>nil(), 
			maker.Block(0, List.of(statements)),  
			null);
		return recursiveSetGeneratedBy(methodDef, annotationNode.get(), typeNode.getContext());
	}
	
	private boolean isBeanClassProvided(AnnotationValues<Convertable> annotation) {
		return annotation.isExplicit(BEAN_ANNOTATION_NAME);
	}
	
	private JCTree.JCExpression getAnnotatedClassType(AnnotationValues<Convertable> annotation, JavacTreeMaker maker, JavacNode typeNode) {
		String beanName = annotation.getRawExpression(BEAN_ANNOTATION_NAME);
		beanName = beanName.lastIndexOf(".class") > 0 ? beanName.substring(0, beanName.length() - 6) : beanName;
		return maker.Ident(typeNode.toName(beanName));
	}
	
	private JCTree.JCStatement buildToBeanJcStatementWithBeanClass(JCTree.JCExpression beanType, lombok.javac.JavacTreeMaker maker, JavacNode typeNode) {
		ListBuffer<JCTree.JCExpression> args = new ListBuffer<JCTree.JCExpression>();
		args.append(maker.Ident(typeNode.toName("this"))).append(maker.Select(beanType, typeNode.toName("class")));
		
		JCTree.JCMethodInvocation convertStatement = maker.Apply(List.<JCTree.JCExpression>nil(), chainDotsString(typeNode, CONVERT_METHOD), args.toList());
		return maker.Return(convertStatement);
	}
	
	private JCTree.JCStatement buildToBeanJcStatementWithGeneric(JavacNode typeNode, lombok.javac.JavacTreeMaker maker) {
        ListBuffer<JCTree.JCExpression> args = new ListBuffer<JCTree.JCExpression>();
        args.append(maker.Ident(typeNode.toName("this")))
                .append(maker.Ident(typeNode.toName(CLAZZ_PARAM_NAME)));

        JCTree.JCMethodInvocation convertStatement = maker.Apply(
                List.<JCTree.JCExpression>nil(),
                chainDotsString(typeNode, CONVERT_METHOD),
                args.toList()
        );
        return maker.Return(convertStatement);
    }
	
    private JCTree.JCVariableDecl buildToBeanParam(JavacNode typeNode, lombok.javac.JavacTreeMaker maker) {
        long flags = addFinalIfNeeded(Flags.PARAMETER, typeNode.getContext());
        return maker.VarDef(maker.Modifiers(flags),
                typeNode.toName(CLAZZ_PARAM_NAME),
                maker.TypeApply(maker.Ident(typeNode.toName("Class")), List.<JCTree.JCExpression>of(maker.Ident(typeNode.toName("T")))),
                null);
    }
    
    private JCTree.JCMethodDecl createFromBeanMethod(AnnotationValues<Convertable> annotation, JavacNode typeNode, JavacNode annotationNode) {
		if(this.isBeanClassProvided(annotation)) {
			return createFromBeanMethodWithBeanClass(annotation, typeNode, annotationNode);
		} else {
			return createFromBeanMethodWithGeneric(annotation, typeNode, annotationNode);
		}
	}

	private JCTree.JCMethodDecl createFromBeanMethodWithBeanClass(AnnotationValues<Convertable> annotation, JavacNode typeNode, JavacNode annotationNode) {
		JavacTreeMaker maker = typeNode.getTreeMaker();
		JCTree.JCModifiers mods = maker.Modifiers(Flags.PUBLIC | Flags.STATIC);
		JCTree.JCExpression paramType = getAnnotatedClassType(annotation, maker, typeNode);
		JCTree.JCVariableDecl parameter = buildFromBeanParamWithBeanClass(paramType, typeNode, maker);
		JCTree.JCClassDecl type = (JCTree.JCClassDecl) typeNode.get();
		JCTree.JCExpression returnType = maker.Ident(type.name);
		JCTree.JCStatement block = buildFromBeanJcStatementWithBeanClass(returnType, typeNode, maker);
		
		JCTree.JCMethodDecl methodDef = maker.MethodDef(mods, 
			typeNode.toName(FROM_BEAN_METHOD_NAME), 
			returnType, List.<JCTree.JCTypeParameter>nil(), 
			List.<JCTree.JCVariableDecl>of(parameter), 
			List.<JCTree.JCExpression>nil(), 
			maker.Block(0, List.of(block)), 
			null);
		createRelevantNonNullAnnotation(typeNode, methodDef);
		return recursiveSetGeneratedBy(methodDef, annotationNode.get(), typeNode.getContext());
	}
	
	private JCTree.JCMethodDecl createFromBeanMethodWithGeneric(AnnotationValues<Convertable> annotation, JavacNode typeNode, JavacNode annotationNode) {
		lombok.javac.JavacTreeMaker maker = typeNode.getTreeMaker();
		JCTree.JCModifiers mods = maker.Modifiers(Flags.PUBLIC | Flags.STATIC);
		JCTree.JCClassDecl type = (JCTree.JCClassDecl) typeNode.get();
		JCTree.JCExpression returnType = maker.Ident(type.name);
		JCTree.JCStatement statements = buildFromBeanStatementWithGeneric(returnType, typeNode, maker);
		JCTree.JCVariableDecl param = buildFromBeanParamWithGeneric(typeNode, maker);
		JCTree.JCTypeParameter typaram = maker.TypeParameter(typeNode.toName("T"), List.<JCTree.JCExpression>nil());
		
		JCTree.JCMethodDecl methodDef = maker.MethodDef(mods, 
			typeNode.toName(FROM_BEAN_METHOD_NAME),
			returnType, 
			List.<JCTree.JCTypeParameter>of(typaram), 
			List.<JCTree.JCVariableDecl>of(param), 
			List.<JCTree.JCExpression>nil(), maker.Block(0, 
				List.of(statements)), 
			null);
		
		createRelevantNonNullAnnotation(typeNode, methodDef);
		return recursiveSetGeneratedBy(methodDef, annotationNode.get(), typeNode.getContext());
	}
	
	private JCTree.JCVariableDecl buildFromBeanParamWithBeanClass(JCTree.JCExpression paramType, JavacNode typeNode, lombok.javac.JavacTreeMaker maker) {
		long flags = addFinalIfNeeded(Flags.PARAMETER, typeNode.getContext());
		return maker.VarDef(maker.Modifiers(flags), typeNode.toName(POJO_PARAM_NAME), paramType, null);
	}
	
	private JCTree.JCStatement buildFromBeanJcStatementWithBeanClass(JCTree.JCExpression returnType, JavacNode typeNode, lombok.javac.JavacTreeMaker maker) {
		ListBuffer<JCTree.JCExpression> args = new ListBuffer<JCTree.JCExpression>();
		args.append(maker.Ident(typeNode.toName(POJO_PARAM_NAME))).append(maker.Select(returnType, typeNode.toName("class")));
		
		JCTree.JCExpression jcExpression = chainDotsString(typeNode, CONVERT_METHOD);
		JCTree.JCMethodInvocation convertStatement = maker.Apply(List.<JCTree.JCExpression>nil(), jcExpression, args.toList());
		return maker.Return(convertStatement);
	}
	
	private JCTree.JCVariableDecl buildFromBeanParamWithGeneric(JavacNode typeNode, lombok.javac.JavacTreeMaker maker) {
        long flags = addFinalIfNeeded(Flags.PARAMETER, typeNode.getContext());
        return maker.VarDef(maker.Modifiers(flags),
                typeNode.toName(POJO_PARAM_NAME),
                maker.Ident(typeNode.toName("T")),
                null);
    }

    private JCTree.JCStatement buildFromBeanStatementWithGeneric(JCTree.JCExpression returnType, JavacNode typeNode, lombok.javac.JavacTreeMaker maker) {
        ListBuffer<JCTree.JCExpression> args = new ListBuffer<JCTree.JCExpression>();
        args.append(maker.Ident(typeNode.toName(POJO_PARAM_NAME)))
                .append(maker.Select(returnType, typeNode.toName("class")));

        JCTree.JCExpression jcExpression = chainDotsString(typeNode, CONVERT_METHOD);
        JCTree.JCMethodInvocation convertStatement = maker.Apply(List.<JCTree.JCExpression>nil(), jcExpression, args.toList());
        return maker.Return(convertStatement);
    }
}

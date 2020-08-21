package lombok.eclipse.handlers;

import lombok.AccessLevel;
import lombok.ConfigurationKeys;
import lombok.JsonSerializable;
import lombok.core.AnnotationValues;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseNode;
import lombok.eclipse.handlers.HandleConstructor.SkipIfConstructorExists;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.mangosdk.spi.ProviderFor;

import java.util.Collections;

import static lombok.core.handlers.HandlerUtil.handleFlagUsage;

/**
 * @author lihongbin
 * Handles the {@code lombok.JsonSerializable} annotation for eclipse.
 */
@ProviderFor(EclipseAnnotationHandler.class)
public class HandleJson extends EclipseAnnotationHandler<JsonSerializable> {
	private HandleGetter handleGetter = new HandleGetter();
	private HandleSetter handleSetter = new HandleSetter();
	private HandleEqualsAndHashCode handleEqualsAndHashCode = new HandleEqualsAndHashCode();
	private HandleToString handleToString = new HandleToString();
	private HandleConstructor handleConstructor = new HandleConstructor();
	
	@Override public void handle(AnnotationValues<JsonSerializable> annotation, Annotation ast, EclipseNode annotationNode) {
		handleFlagUsage(annotationNode, ConfigurationKeys.DATA_FLAG_USAGE, "@JsonSerializable");

		JsonSerializable ann = annotation.getInstance();
		EclipseNode typeNode = annotationNode.up();
		
		TypeDeclaration typeDecl = null;
		if (typeNode.get() instanceof TypeDeclaration) typeDecl = (TypeDeclaration) typeNode.get();
		int modifiers = typeDecl == null ? 0 : typeDecl.modifiers;
		boolean notAClass = (modifiers &
				(ClassFileConstants.AccInterface | ClassFileConstants.AccAnnotation | ClassFileConstants.AccEnum)) != 0;
		
		if (typeDecl == null || notAClass) {
			annotationNode.addError("@JsonSerializable is only supported on a class.");
			return;
		}
		
		handleGetter.generateGetterForType(typeNode, annotationNode, AccessLevel.PUBLIC, true, Collections.<Annotation>emptyList());
		handleSetter.generateSetterForType(typeNode, annotationNode, AccessLevel.PUBLIC, true, Collections.<Annotation>emptyList(), Collections.<Annotation>emptyList());
		handleEqualsAndHashCode.generateEqualsAndHashCodeForType(typeNode, annotationNode);
		handleToString.generateToStringForType(typeNode, annotationNode);
		handleConstructor.generateRequiredArgsConstructor(
				typeNode, AccessLevel.PUBLIC, ann.staticConstructor(), SkipIfConstructorExists.YES,
				Collections.<Annotation>emptyList(), annotationNode);
		handleConstructor.generateExtraNoArgsConstructor(typeNode, annotationNode);
	}
}

package lombok.javac.handlers;

import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.util.List;
import lombok.AccessLevel;
import lombok.ConfigurationKeys;
import lombok.JsonSerializable;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.handlers.HandleConstructor.SkipIfConstructorExists;
import org.mangosdk.spi.ProviderFor;

import static lombok.core.handlers.HandlerUtil.handleFlagUsage;
import static lombok.javac.handlers.JavacHandlerUtil.deleteAnnotationIfNeccessary;
import static lombok.javac.handlers.JavacHandlerUtil.isClass;

/**@author lihongbin
 * Handles the {@code lombok.JsonSerializable} annotation for javac.
 */
@ProviderFor(JavacAnnotationHandler.class)
public class HandleJson extends JavacAnnotationHandler<JsonSerializable> {
	private HandleConstructor handleConstructor = new HandleConstructor();
	private HandleGetter handleGetter = new HandleGetter();
	private HandleSetter handleSetter = new HandleSetter();
	private HandleEqualsAndHashCode handleEqualsAndHashCode = new HandleEqualsAndHashCode();
	private HandleToString handleToString = new HandleToString();

	@Override public void handle(AnnotationValues<JsonSerializable> annotation, JCAnnotation ast, JavacNode annotationNode) {
		handleFlagUsage(annotationNode, ConfigurationKeys.DATA_FLAG_USAGE, "@JsonSerializable");

		deleteAnnotationIfNeccessary(annotationNode, JsonSerializable.class);
		JavacNode typeNode = annotationNode.up();
		boolean notAClass = !isClass(typeNode);

		if (notAClass) {
			annotationNode.addError("@JsonSerializable is only supported on a class.");
			return;
		}

		String staticConstructorName = annotation.getInstance().staticConstructor();

		// TODO move this to the end OR move it to the top in eclipse.
		handleConstructor.generateRequiredArgsConstructor(typeNode, AccessLevel.PUBLIC, staticConstructorName, SkipIfConstructorExists.YES, annotationNode);
		handleConstructor.generateExtraNoArgsConstructor(typeNode, annotationNode);
		handleGetter.generateGetterForType(typeNode, annotationNode, AccessLevel.PUBLIC, true, List.<JCAnnotation>nil());
		handleSetter.generateSetterForType(typeNode, annotationNode, AccessLevel.PUBLIC, true, List.<JCAnnotation>nil(), List.<JCAnnotation>nil());
		handleEqualsAndHashCode.generateEqualsAndHashCodeForType(typeNode, annotationNode);
		handleToString.generateToStringForType(typeNode, annotationNode);
	}
}

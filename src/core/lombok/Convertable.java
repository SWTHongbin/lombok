package lombok;

/**
 * @author lihongbin
 * <p>
 * convert pojo to Class
 * <p>
 * public class ConvertExample {
 * <p>
 * public <T> T toBean(Class<T> cls) throws IllegalAccessException, InstantiationException {
 * return cls.newInstance();
 * }
 * <p>
 * public static <T> ConvertExample fromBean(T t) {
 * ConvertExample convertExample = new ConvertExample();
 * return convertExample;
 * }
 * <p>
 * //get/set methds here
 * .........
 * }
 */
public @interface Convertable {
}

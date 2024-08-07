package cn.bixin.sona.gateway.handler;

import cn.bixin.sona.gateway.exception.RemoteException;
import io.netty.util.internal.PlatformDependent;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.utils.ClassUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * @author qinwei
 */
@Slf4j
@Component
public class MercuryRouter implements ApplicationListener<ContextRefreshedEvent> {

    private static final Map<Integer, String> HANDLES = new HashMap<>();

    public static final String DIR = "META-INF/mercury/";

    private static ApplicationContext applicationContext;

    private static final Map<String, Handler> HANDLER_WRAPPERS = PlatformDependent.newConcurrentHashMap();

    public static Handler router(int cmd) throws RemoteException {
        String name = HANDLES.get(cmd);
        if (!StringUtils.hasText(name)) {
            throw new RemoteException("MercuryRouter failure , unable to find the cmd " + cmd + " !");
        }
        //根据指定的键来获取对应的值，如果键不存在，则会使用提供的函数计算出一个值，并将其放入映射中
        return HANDLER_WRAPPERS.computeIfAbsent(name, MercuryRouter::wrapHandler);
    }

    private static HandlerWrapper wrapHandler(String name) {
        return new HandlerWrapper(name, applicationContext.getBean(name, Handler.class));
    }

    /**
     * 在 Spring 应用上下文刷新时，初始化 applicationContext 并加载 Handler 类相关的资源文件
     * <p>
     * 应用启动时：当Spring应用启动并完成所有的Bean初始化后，会触发ContextRefreshedEvent。
     * 调用ConfigurableApplicationContext的refresh()方法时：手动调用refresh()方法也会触发上下文刷新事件。
     *
     * @param event Spring 应用上下文刷新事件
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        applicationContext = event.getApplicationContext();
        loadDirectory(Handler.class);
    }

    private void loadDirectory(Class<?> clazz) {
        String fileName = DIR + clazz.getName();
        try {
            Enumeration<URL> urls = ClassUtils.getClassLoader().getResources(fileName);
            if (urls != null) {
                while (urls.hasMoreElements()) {
                    loadResource(clazz, urls.nextElement());
                }
            }
        } catch (Throwable t) {
            log.error("Exception occurred when loading config (interface: " + clazz.getName() + ", description file: " + fileName + ").", t);
        }
    }


    /**
     * 加载资源文件并解析其内容，将解析结果存储到 HANDLES 映射中
     *
     * @param clazz 资源文件对应的类
     * @param url   资源文件的 URL
     */
    private void loadResource(Class<?> clazz, URL url) {
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final int ci = line.indexOf('#');
                    if (ci >= 0) {
                        line = line.substring(0, ci);
                    }
                    line = line.trim();
                    if (line.length() > 0) {
                        try {
                            String name = null;
                            int i = line.indexOf('=');
                            if (i > 0) {
                                name = line.substring(0, i).trim();
                                line = line.substring(i + 1).trim();
                            }
                            if (name != null && line.length() > 0) {
                                String[] names = name.split(",");
                                for (String s : names) {
                                    HANDLES.put(Integer.parseInt(s), line);
                                }
                            }
                        } catch (Throwable t) {
                            log.error("Failed to load config (interface: " + clazz.getName() + ", class line: " + line + ") in " + url + ", cause: " + t.getMessage(), t);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            log.error("Exception occurred when loading config (interface: " + clazz.getName() + ", class file: " + url + ") in " + url, t);
        }
    }

}

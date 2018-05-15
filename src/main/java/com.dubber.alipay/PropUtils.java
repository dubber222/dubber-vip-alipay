package com.dubber.alipay;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Created on 2018/5/16.
 *
 * @author dubber
 */
public class PropUtils {
    private static final Logger logger = Logger.getLogger(PropUtils.class);

    private static Properties props = null;

    public static void init() {
        if (props == null) {
            props = new Properties();
            InputStream in = null;
            try {
                String url = PropUtils.class.getResource("").getPath().replaceAll("%20", " ");
                String file = url.substring(0, url.indexOf("WEB-INF")) + "WEB-INF/payment/config.properties";
                in = new FileInputStream(new File(file));
                props.load(in);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        }
    }

    public static String getProps(String key) {
        if (props == null || props.size() == 0)
            init();
        return props.getProperty(key);
    }

}

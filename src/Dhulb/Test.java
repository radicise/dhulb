package Dhulb;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

import DExt.DhulbExtension;

public class Test {
    public static void main(String[] args) throws Exception {
        @SuppressWarnings("unchecked")
        Class<? extends DhulbExtension> cls = (Class<? extends DhulbExtension>) Class.forName("DExt.DhulbArray");
        // get main method
        Method m = cls.getDeclaredMethod("dothing", new Class[]{InputStream.class, OutputStream.class});
        // InputStream send = new ByteArrayInputStream("Extrainious u32[]".getBytes());
        InputStream send = new ByteArrayInputStream("u32[]".getBytes());
        RecoverableOutputStream rec = new RecoverableOutputStream();
        // invoke main method
        m.invoke(null, new Object[]{send, rec});
        System.out.println(new String(rec.data));
    }
}

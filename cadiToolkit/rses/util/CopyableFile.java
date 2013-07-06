package rses.util;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;

public class CopyableFile extends File {

    public CopyableFile(String pathname) {
        super(pathname);
    }

    public CopyableFile(URI uri) {
        super(uri);
    }

    public CopyableFile(File parent, String child) {
        super(parent, child);
    }

    public CopyableFile(String parent, String child) {
        super(parent, child);
    }

    public void copyTo(File destination) throws IOException {
        BufferedInputStream bIS = null;
        BufferedOutputStream bOS = null;

        try {
            bIS = new BufferedInputStream(new FileInputStream(this));
            bOS = new BufferedOutputStream(new FileOutputStream(destination));
            byte[] buffer = new byte[2048];
            int bytesRead;
            while ((bytesRead = bIS.read(buffer, 0, buffer.length)) != -1) {
                bOS.write(buffer, 0, bytesRead);
            }
            bOS.flush();
        } finally {
            if (bIS != null) {
                try {
                    bIS.close();
                    bIS = null;
                } catch(IOException e) {
                }
            }
            if (bOS != null) {
                try {
                    bOS.close();
                    bOS = null;
                } catch (IOException e) {
                }
            }
        }
    }
}

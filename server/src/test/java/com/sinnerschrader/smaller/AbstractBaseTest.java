package com.sinnerschrader.smaller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.sinnerschrader.smaller.common.Zip;

import static org.junit.Assert.*;

import static org.hamcrest.CoreMatchers.*;

/**
 * @author marwol
 */
public abstract class AbstractBaseTest {

  private static ServerRunnable serverRunnable;

  /** */
  @BeforeClass
  public static void startServer() {
    serverRunnable = new ServerRunnable();
    new Thread(new ServerRunnable()).start();
    try {
      Thread.sleep(1500);
    } catch (InterruptedException e) {
    }
  }

  /** */
  @AfterClass
  public static void stopServer() {
    serverRunnable.stop();
  }

  protected void runToolChain(final String file, final ToolChainCallback callback) throws Exception {
    boolean createZip = false;
    final File temp = File.createTempFile("smaller-test-", ".zip");
    assertTrue(temp.delete());
    final File target = File.createTempFile("smaller-test-", ".dir");
    assertTrue(target.delete());
    assertTrue(target.mkdir());
    File zip = FileUtils.toFile(getClass().getResource("/" + file));
    try {
      if (zip.isDirectory()) {
        createZip = true;
        File out = File.createTempFile("temp-", ".zip");
        out.delete();
        Zip.zip(new FileOutputStream(out), zip);
        zip = out;
      }
      uploadZipFile(zip, temp, new Callback() {
        public void execute() throws Exception {
          Zip.unzip(temp, target);
          callback.test(target);
        }
      });
    } finally {
      FileUtils.deleteDirectory(target);
      temp.delete();
      if (createZip) {
        zip.delete();
      }
    }
  }

  private void uploadZipFile(File zip, File response, Callback callback) throws Exception {
    HttpClient client = new HttpClient();
    PostMethod post = new PostMethod("http://localhost:1148");
    try {
      post.setRequestEntity(new FileRequestEntity(zip, "application/zip"));
      int statusCode = client.executeMethod(post);
      assertThat(statusCode, is(HttpStatus.SC_OK));
      InputStream responseBody = post.getResponseBodyAsStream();
      FileUtils.writeByteArrayToFile(response, IOUtils.toByteArray(responseBody));
      callback.execute();
    } finally {
      post.releaseConnection();
    }
  }

  private static class ServerRunnable implements Runnable {

    private Server server;

    public ServerRunnable() {
      server = new Server();
    }

    /**
     * @see java.lang.Runnable#run()
     */
    public void run() {
      server.start(new String[] {});
    }

    public void stop() {
      server.stop();
    }

  }

  private interface Callback {

    void execute() throws Exception;

  }

  protected interface ToolChainCallback {

    void test(File directory) throws Exception;

  }

}

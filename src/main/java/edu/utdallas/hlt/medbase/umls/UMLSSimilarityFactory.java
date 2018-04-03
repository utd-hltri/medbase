package edu.utdallas.hlt.medbase.umls;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

@SuppressWarnings("unused")
public class UMLSSimilarityFactory {
  final InetAddress address;
  final int port;

  public UMLSSimilarityFactory(InetAddress address, int port) {
    this.address = address;
    this.port = port;
  }

  public static enum Similarity {
    CONCEPTUAL_DISTANCE ("cdist"),
    JIANG_CONRATH       ("jcn"),
    LEACOCK_CHODOROW    ("lch"),
    LESK                ("lesk"),
    LIN                 ("lin"),
    NGUYEN_ALMUBAID     ("nam"),
    EDGE_COUNTING       ("path"),
    RANDOM              ("random"),
    RESNIK              ("res"),
    CONTEXT_VECTOR      ("vector"),
    WU_PALMER           ("wup"),
    ZHONG               ("zhong");

    public final String name;

    private Similarity(String name) {
      this.name = name;
    }
  }


  public UMLSMetric getUMLSSimilarity(final Similarity similarity) {
    return new UMLSMetric(address, port, similarity);
  }

  public static class UMLSMetric implements Closeable {

    final Socket socket;
    final BufferedWriter writer;
    final BufferedReader reader;
    final Similarity similarity;

    public UMLSMetric(InetAddress address, int port, Similarity similarity) {
      try {
        socket = new Socket(address, port);
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.similarity = similarity;
      }  catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    public double apply(final String t1, final String t2) {
      try {
        // Send message
        writer.write(t1 + ' ' + t2 + ' ' + similarity.name);
        writer.newLine();
        writer.flush();

        // Get response
        final String similarity = reader.readLine();
        return Double.valueOf(similarity);
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

     public void close() {
      try {
        reader.close();
        writer.close();
        socket.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}

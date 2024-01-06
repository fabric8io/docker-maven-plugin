package io.fabric8.dmp.sample.multiarch.helloworld;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Hello World from " + System.getProperty("os.arch"));
        Thread.sleep(100000);
    }
}

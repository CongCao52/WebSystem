package cis5550.webserver;

@FunctionalInterface
public interface Filter {
  void handle(Request request, Response response) throws Exception;
}
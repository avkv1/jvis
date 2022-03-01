package ru.akuleshov.jvis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.bcel.classfile.*;
import org.apache.bcel.util.ByteSequence;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Application {

  private static final String PARSE_REGEX = "\\(\\S+\\)|\\(\\)";
  private static final Pattern pattern = Pattern.compile(PARSE_REGEX);

  public static void main(String[] args) throws IOException {
    File file = new File("./data/ExampleController.class");
    InputStream istream = new FileInputStream(file);

    ClassParser parser = new ClassParser(istream, "ExampleController.class");

    JavaClass clazz = parser.parse();
    TracedClass tracedClass = tracedClass(clazz);
    System.out.println(tracedClass);

    ObjectMapper mapper = new ObjectMapper();
    String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tracedClass);

    BufferedWriter writer = new BufferedWriter(new FileWriter("out.json"));
    writer.write(json);

    writer.close();
  }

  private static TracedClass tracedClass(JavaClass clazz) {

    return TracedClass.builder()
        .kind("Controller")
        .clazz(clazz.getClassName())
        .method(tracedMethods(clazz))
        .build();
  }

  private static List<TracedMethod> tracedMethods(JavaClass javaClass) {
    List<TracedMethod> methods = new ArrayList<>();

    for (Method method : javaClass.getMethods()) {
      ConstantUtf8 c =
          (ConstantUtf8) method.getConstantPool().getConstant(method.getSignatureIndex(), (byte) 1);

      String name = c.getBytes();
      String sig = method.getSignature();

      Code code = method.getCode();
      List<Operation> ops = codeToString(code.getCode(), code.getConstantPool(), 0, -1, true)
              .stream()
              .filter(f -> !f.getOp().startsWith("iload_") && !f.getOp().startsWith("aload_"))
              .collect(Collectors.toList());

      TracedMethod tracedMethod =
          TracedMethod.builder()
              .method(method.getName())
              .signature(sig)
              .input(parseMethodArgs(sig))
              .output(parseMethodOut(sig))
              .ops(ops)
              .build();

      methods.add(tracedMethod);
    }

    return methods;
  }

  private static String parseMethodOut(String sig) {
    Matcher m = pattern.matcher(sig);
    if (m.find()) {
      String args = m.group(0);
      return sig.replace(args, "");
    }
    return sig;
  }

  private static List<String> parseMethodArgs(String sig) {
    List<String> args = new ArrayList<>();
    Matcher m = pattern.matcher(sig);
    if (m.find()) {
      args.add(m.group(0));
    }
    return args;
  }

  private static List<TracedOperation> traceOps(List<Operation> ops) {
    List<TracedOperation> traced = new ArrayList<>(ops.size());
    for (Operation operation : ops) {
      traced.add(trace(operation));
    }
    return traced;
  }

  private static TracedOperation trace(Operation operation) {
    return TracedOperation.builder().build();
  }

  public static List<Operation> codeToString(
      byte[] code, ConstantPool constant_pool, int index, int length, boolean verbose) {
    List<Operation> operations = new ArrayList<>();
    StringBuilder buf = new StringBuilder(code.length * 20);

    try (ByteSequence stream = new ByteSequence(code)) {
      try {
        for (int i = 0; stream.available() > 0; ++i) {
          if (length < 0 || i < length) {
            String c = Utility.codeToString(stream, constant_pool, verbose);
            Operation op = parseOp(c);
            operations.add(op);
          }
        }
      } catch (Throwable t) {
        throw t;
      }
    } catch (IOException e) {
      throw new ClassFormatException("Byte code error: " + buf, e);
    }

    return operations;
  }

  private static Operation parseOp(String zz) {
    String[] tmp = zz.split("\t\t");
    String op = tmp[0];
    String var = null;
    String type = null;
    if (tmp.length > 1) {
      String tmp1 = tmp[1];
      tmp = tmp1.split(" ");
      var = tmp[0];
      type = tmp[1];
    } else {
      tmp = zz.split("\t");
      if (tmp.length > 1) {
        op = tmp[0];
        tmp = tmp[1].split(" ");
        var = tmp[0];
        type = tmp[1];
      }
    }
    return Operation.builder().op(op).var(var).type(type).build();
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class Operation {
    private String op;
    private String var;
    private String type;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class TracedClass {
    private String kind;
    private String clazz;
    private List<TracedMethod> method;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class TracedMethod {
    private String method;
    private String signature;
    private List<String> input;
    private String output;
    private List<Operation> ops;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class TracedOperation {
    private String op;
    private String var;
    private String type;
  }
}

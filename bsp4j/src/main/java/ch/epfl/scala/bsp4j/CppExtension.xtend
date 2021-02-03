package ch.epfl.scala.bsp4j

import java.util.List
import org.eclipse.lsp4j.jsonrpc.validation.NonNull
import org.eclipse.lsp4j.generator.JsonRpcData

@JsonRpcData
class CppBuildTarget {
  @NonNull CppPlatform platform
  String cCompiler
  String cppCompiler
  new(@NonNull CppPlatform platform, String cCompiler, String cppCompiler) {
    this.platform = platform
    this.cCompiler = cCompiler
    this.cppCompiler = cppCompiler
  }
}


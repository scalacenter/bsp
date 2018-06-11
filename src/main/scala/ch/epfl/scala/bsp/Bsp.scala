package ch.epfl.scala.bsp

import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, HCursor, Json, JsonObject, ObjectEncoder, RootEncoder}
import io.circe.generic.JsonCodec

@JsonCodec final case class TextDocumentIdentifier(
    uri: String
)

@JsonCodec final case class BuildTargetIdentifier(
    uri: String
)

@JsonCodec final case class BuildTargetCapabilities(
    canCompile: Boolean,
    canTest: Boolean,
    canRun: Boolean,
)

@JsonCodec final case class BuildTarget(
    id: BuildTargetIdentifier,
    displayName: String,
    languageIds: List[String],
    dependencies: BuildTargetIdentifier,
    capabilities: BuildTargetCapabilities,
    data: Option[Json]
)

@JsonCodec final case class BuildClientCapabilities(
    languageIds: List[String]
)

// Notification: 'build/initialized', C -> S
@JsonCodec final case class InitializedBuildParams()

// Request: 'build/initialize', C -> S
@JsonCodec final case class InitializeBuildParams(
    rootUri: String,
    capabilities: BuildClientCapabilities
)

@JsonCodec final case class Shutdown()

@JsonCodec final case class Exit()

@JsonCodec final case class CompileProvider(
    languageIds: List[String]
)

@JsonCodec final case class TestProvider(
    languageIds: List[String]
)

@JsonCodec final case class RunProvider(
    languageIds: List[String]
)

@JsonCodec final case class BuildServerCapabilities(
    compileProvider: CompileProvider,
    testProvider: TestProvider,
    runProvider: RunProvider,
    providesTextDocumentBuildTargets: Boolean,
    providesDependencySources: Boolean,
    providesResources: Boolean,
    providesBuildTargetChanged: Boolean
)

@JsonCodec final case class InitializeBuildResult(
    capabilities: BuildServerCapabilities
)

sealed abstract class MessageType(val id: Int)
object MessageType {
  case object Error extends MessageType(1)
  case object Warning extends MessageType(2)
  case object Info extends MessageType(3)
  case object Log extends MessageType(4)

  implicit val messageTypeEncoder: RootEncoder[MessageType] = new RootEncoder[MessageType] {
    override def apply(a: MessageType): Json = Json.fromInt(a.id)
  }

  implicit val messageTypeDecoder: Decoder[MessageType] = new Decoder[MessageType] {
    override def apply(c: HCursor): Result[MessageType] = {
      c.as[Int].flatMap {
        case 1 => Right(Error)
        case 2 => Right(Warning)
        case 3 => Right(Info)
        case 4 => Right(Log)
        case n => Left(DecodingFailure(s"Unknown message type id for $n", c.history))
      }
    }
  }
}

@JsonCodec final case class HierarchicalId(
    id: String,
    parentId: String
)

@JsonCodec final case class ShowMessageParams(
    `type`: MessageType,
    id: Option[HierarchicalId],
    requestId: Option[String],
    message: String
)

@JsonCodec final case class LogMessageParams(
    `type`: MessageType,
    id: Option[HierarchicalId],
    requestId: Option[String],
    message: String
)

@JsonCodec final case class PublishDiagnosticsParams(
    uri: String,
    requestId: Option[String],
    message: String
)

@JsonCodec final case class WorkspaceBuildTargetsRequest()

// Request: 'workspace/buildTargets'
@JsonCodec final case class WorkspaceBuildTargets(
    targets: List[BuildTarget]
)

sealed abstract class BuildTargetEventKind(val id: Int)
case object BuildTargetEventKind {
  case object Created extends BuildTargetEventKind(1)
  case object Changed extends BuildTargetEventKind(2)
  case object Deleted extends BuildTargetEventKind(3)

  implicit val buildTargetEventKindEncoder: RootEncoder[BuildTargetEventKind] =
    new RootEncoder[BuildTargetEventKind] {
      override def apply(a: BuildTargetEventKind): Json = Json.fromInt(a.id)
    }

  implicit val buildTargetEventKindDecoder: Decoder[BuildTargetEventKind] =
    new Decoder[BuildTargetEventKind] {
      override def apply(c: HCursor): Result[BuildTargetEventKind] = {
        c.as[Int].flatMap {
          case 1 => Right(Created)
          case 2 => Right(Changed)
          case 3 => Right(Deleted)
          case n => Left(DecodingFailure(s"Unknown build target event kind id for $n", c.history))
        }
      }
    }
}

@JsonCodec final case class BuildTargetEvent(
    id: BuildTargetIdentifier,
    kind: Option[BuildTargetEventKind],
    data: Option[Json]
)

// Notification: 'buildTarget/didChange', S -> C
@JsonCodec final case class DidChangeBuildTarget(
    changes: List[BuildTargetEvent]
)

// Request: 'buildTarget/textDocument', C -> S
@JsonCodec final case class BuildTargetTextDocumentParams(
    targets: List[BuildTargetIdentifier]
)

@JsonCodec final case class BuildTargetTextDocumentsResult(
    textDocuments: List[TextDocumentIdentifier]
)

// Request: 'textDocument/buildTarget', C -> S
@JsonCodec final case class TextDocumentBuildTargetsParams(
    textDocument: TextDocumentIdentifier
)

@JsonCodec final case class TextDocumentBuildTargetsResult(
    targets: List[BuildTarget]
)

// Request: 'buildTarget/dependencySources', C -> S
@JsonCodec final case class DependencySourcesParams(
    targets: List[BuildTargetIdentifier]
)

@JsonCodec final case class DependencySourcesItem(
    target: BuildTargetIdentifier,
    uris: List[String]
)

@JsonCodec final case class DependencySourcesResult(
    items: List[DependencySourcesItem]
)

// Request: 'buildTarget/resources', C -> S
@JsonCodec final case class ResourcesParams(
    targets: List[BuildTargetIdentifier]
)

@JsonCodec final case class ResourcesItem(
    target: BuildTargetIdentifier,
    uris: List[String]
)

@JsonCodec final case class ResourcesResult(
    targets: List[ResourcesItem]
)

// Request: 'buildTarget/compile', C -> S
@JsonCodec final case class CompileParams(
    targets: List[BuildTargetIdentifier],
    requestId: Option[String],
    arguments: List[Json]
)

@JsonCodec final case class CompileResult(
    requestId: Option[String],
    data: Option[Json]
)

@JsonCodec final case class CompileReport(
    target: BuildTargetIdentifier,
    requestId: Option[String],
    errors: Int,
    warnings: Int,
    time: Option[Long]
)

@JsonCodec final case class TestParams(
    targets: List[BuildTargetIdentifier],
    requestId: Option[String],
    arguments: List[Json]
)

@JsonCodec final case class TestResult(
    requestId: Option[String],
    data: Option[Json]
)

@JsonCodec final case class TestReport(
    target: BuildTargetIdentifier,
    requestId: Option[String],
    passed: Int,
    failed: Int,
    ignored: Int,
    cancelled: Int,
    skipped: Int,
    pending: Int,
    time: Option[Long]
)

@JsonCodec final case class RunParams(
    target: BuildTargetIdentifier,
    requestId: Option[String],
    arguments: List[Json]
)

sealed abstract class ExitStatus(val code: Int)
object ExitStatus {
  case object Error extends ExitStatus(1)
  case object Ok extends ExitStatus(2)
  case object Cancelled extends ExitStatus(3)

  implicit val exitStatusEncoder: RootEncoder[ExitStatus] = new RootEncoder[ExitStatus] {
    override def apply(a: ExitStatus): Json = Json.fromInt(a.code)
  }

  implicit val exitStatusDecoder: Decoder[ExitStatus] = new Decoder[ExitStatus] {
    override def apply(c: HCursor): Result[ExitStatus] = {
      c.as[Int].flatMap {
        case 1 => Right(Error)
        case 2 => Right(Ok)
        case 3 => Right(Cancelled)
        case n => Left(DecodingFailure(s"Unknown exit status for code $n", c.history))
      }
    }
  }
}

@JsonCodec final case class RunResult(
    requestId: Option[String],
    exitStatus: ExitStatus
)

sealed abstract class ScalaPlatform(val id: Int)
object ScalaPlatform {
  case object Jvm extends ScalaPlatform(1)
  case object Js extends ScalaPlatform(2)
  case object Native extends ScalaPlatform(3)

  implicit val scalaPlatformEncoder: RootEncoder[ScalaPlatform] = new RootEncoder[ScalaPlatform] {
    override def apply(a: ScalaPlatform): Json = Json.fromInt(a.id)
  }

  implicit val scalaPlatformDecoder: Decoder[ScalaPlatform] = new Decoder[ScalaPlatform] {
    override def apply(c: HCursor): Result[ScalaPlatform] = {
      c.as[Int].flatMap {
        case 1 => Right(Jvm)
        case 2 => Right(Js)
        case 3 => Right(Native)
        case n => Left(DecodingFailure(s"Unknown platform id for $n", c.history))
      }
    }
  }
}

@JsonCodec final case class ScalaBuildTarget(
    scalaOrganization: String,
    scalaVersion: String,
    scalaBinaryVersion: String,
    platform: ScalaPlatform,
    jars: List[String]
)

// Request: 'buildTarget/scalacOptions', C -> S
@JsonCodec final case class ScalacOptionsParams(
    targets: List[BuildTargetIdentifier]
)

@JsonCodec final case class ScalacOptionsItem(
    target: BuildTargetIdentifier,
    options: List[String],
    classpath: List[String],
    classDirectory: String,
)

@JsonCodec final case class ScalacOptionsResult(
    items: List[ScalacOptionsItem]
)

// Request: 'buildTarget/scalaTestClasses', C -> S
@JsonCodec final case class ScalaTestClassesParams(
    targets: List[BuildTargetIdentifier],
    requestId: Option[String],
)

@JsonCodec final case class ScalaTestClassesItem(
    target: BuildTargetIdentifier,
    // Fully qualified names of test classes
    classes: List[String]
)

@JsonCodec final case class ScalaTestClassesResult(
    items: List[ScalaTestClassesItem]
)

// Request: 'buildTarget/scalaMainClasses', C -> S
@JsonCodec final case class ScalaMainClassesParams(
    targets: List[BuildTargetIdentifier],
    requestId: Option[String],
)

@JsonCodec final case class ScalaMainClass(
    `class`: String,
    arguments: List[String],
    javaOptions: List[String]
)

@JsonCodec final case class ScalaMainClassesItem(
    target: BuildTargetIdentifier,
    // Fully qualified names of test classes
    classes: List[ScalaMainClass]
)

@JsonCodec final case class ScalaMainClassesResult(
    items: List[ScalaMainClassesItem]
)

@JsonCodec final case class SbtBuildTarget(
    parent: Option[BuildTargetIdentifier],
    sbtVersion: String,
    scalaVersion: String,
    scalaJars: List[String],
    autoImports: List[String],
    classpath: List[String],
)
package ch.epfl.scala.bsp.mock
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

import ch.epfl.scala.bsp
import ch.epfl.scala.bsp._
import ch.epfl.scala.bsp.mock.mockServers._
import io.circe.syntax._
import monix.eval.Task
import scribe.Logger

import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration
import scala.meta.jsonrpc.{Response, Services}

object mockServers {
  type ProtocolError = Response.Error
  type BspResponse[T] = Task[Either[ProtocolError, T]]
}

abstract class AbstractMockServer {
  val logger = new Logger
  val services: Services = Services
    .empty(logger)
    .requestAsync(endpoints.Build.initialize)(initialize)
    .notification(endpoints.Build.initialized)(initialized)
    .request(endpoints.Build.shutdown)(shutdown)
    .notificationAsync(endpoints.Build.exit)(exit(_))
    .requestAsync(endpoints.Workspace.buildTargets)(buildTargets)
    .requestAsync(endpoints.BuildTarget.dependencySources)(dependencySources)
    .requestAsync(endpoints.BuildTarget.inverseSources)(inverseSources)
    .requestAsync(endpoints.BuildTarget.scalacOptions)(scalacOptions(_))
    .requestAsync(endpoints.BuildTarget.compile)(compile(_))
    .requestAsync(endpoints.BuildTarget.test)(test(_))
    .requestAsync(endpoints.BuildTarget.run)(run(_))

  def initialize(params: InitializeBuildParams): BspResponse[InitializeBuildResult]
  def initialized(params: InitializedBuildParams): Unit
  def shutdown(shutdown: bsp.Shutdown): Unit
  def exit(exit: Exit): Task[Unit]
  def buildTargets(request: WorkspaceBuildTargetsRequest): BspResponse[WorkspaceBuildTargets]
  def dependencySources(params: DependencySourcesParams): BspResponse[DependencySourcesResult]
  def inverseSources(params: InverseSourcesParams): BspResponse[InverseSourcesResult]
  def scalacOptions(params: ScalacOptionsParams): BspResponse[ScalacOptionsResult]
  def compile(params: CompileParams): BspResponse[CompileResult]
  def test(params: TestParams): BspResponse[TestResult]
  def run(params: RunParams): BspResponse[RunResult]
}

/** Mock server that gives a happy successful result to any request.
  */
class HappyMockServer(base: File) extends AbstractMockServer {

  val isInitialized: Promise[Either[ProtocolError, Unit]] = scala.concurrent.Promise[Either[ProtocolError, Unit]]()
  val isShutdown: Promise[Either[ProtocolError, Unit]] = scala.concurrent.Promise[Either[ProtocolError, Unit]]()
  val isShutdownTask: Task[Either[ProtocolError, Unit]] = Task.fromFuture(isShutdown.future).memoize

  override def initialize(params: InitializeBuildParams): BspResponse[InitializeBuildResult] =
    Task {
      val result = bsp.InitializeBuildResult("BSP Mock Server", "1.0", "2.0", capabilities, None)
      Right(result)
    }

  override def initialized(params: InitializedBuildParams): Unit = {
    isInitialized.success(Right(()))
  }

  override def shutdown(shutdown: bsp.Shutdown): Unit = {
    isShutdown.success(Right())
    ()
  }

  override def exit(exit: Exit): Task[Unit] = {
    isShutdownTask
      .map(_ => ())
      .timeoutTo(
        FiniteDuration(1, TimeUnit.SECONDS),
        Task.now(())
      )
  }

  override def buildTargets(request: WorkspaceBuildTargetsRequest): BspResponse[WorkspaceBuildTargets] = {

    val target1Capabilities = BuildTargetCapabilities(canCompile = true, canTest = false, canRun = false)
    val target2Capabilities = BuildTargetCapabilities(canCompile = true, canTest = true, canRun = false)
    val target3Capabilities = BuildTargetCapabilities(canCompile = true, canTest = false, canRun = true)

    val languageIds = List("scala")

    val scalaJars = List("scala-compiler", "scala-reflect", "scala-library").map(Uri.apply)
    val scalaBuildTarget = ScalaBuildTarget("org.scala-lang", "2.12.7", "2.12", ScalaPlatform.Jvm, scalaJars)
    val scalaData = Some(scalaBuildTarget.asJson)

    val targets = List(
      BuildTarget(target1, Some("target 1"), Some(target1.uri), List(BuildTargetTag.Library), target1Capabilities, languageIds, List.empty, scalaData),
      BuildTarget(target2, Some("target 2"), Some(target2.uri), List(BuildTargetTag.Test), target2Capabilities, languageIds, List(target1), scalaData),
      BuildTarget(target3, Some("target 3"), Some(target3.uri), List(BuildTargetTag.Application), target3Capabilities, languageIds, List(target1), scalaData)
    )

    val result = WorkspaceBuildTargets(targets)

    Task(Right(result))
  }

  override def dependencySources(params: DependencySourcesParams): BspResponse[DependencySourcesResult] = {

    val target1Sources = List("lib/Library.scala","lib/Helper.scala", "lib/some-library.jar").map(uriInTarget(target1,_))
    val target2Sources = List("lib/LibraryTest.scala","lib/HelperTest.scala", "lib/some-library.jar").map(uriInTarget(target2,_))
    val target3Sources = List("lib/App.scala", "lib/some-library.jar").map(uriInTarget(target3,_))
    val item1 = DependencySourcesItem(target1, target1Sources)
    val item2 = DependencySourcesItem(target2, target2Sources)
    val item3 = DependencySourcesItem(target3, target3Sources)
    val result = DependencySourcesResult(List(item1,item2,item3))

    Task(Right(result))
  }

  override def inverseSources(params: InverseSourcesParams): BspResponse[InverseSourcesResult] = {
    val result = InverseSourcesResult(List(target1, target2, target3))
    Task(Right(result))
  }

  override def scalacOptions(params: ScalacOptionsParams): BspResponse[ScalacOptionsResult] = {
    val classpath = List(Uri("scala-library.jar"))
    val item1 = ScalacOptionsItem(target1, Nil, classpath, uriInTarget(target1, "out"))
    val item2 = ScalacOptionsItem(target2, Nil, classpath, uriInTarget(target2, "out"))
    val item3 = ScalacOptionsItem(target3, Nil, classpath, uriInTarget(target3, "out"))
    val result = ScalacOptionsResult(List(item1, item2, item3))
    Task(Right(result))
  }

  override def compile(params: CompileParams): BspResponse[CompileResult] = {
    // TODO some task notifications
    val result = CompileResult(params.originId, StatusCode.Ok, None)
    Task(Right(result))
  }
  override def test(params: TestParams): BspResponse[TestResult] = {
    // TODO some test task/report notifications
    // TODO some individual test notifications
    val result = TestResult(params.originId, StatusCode.Ok, None)
    Task(Right(result))
  }
  override def run(params: RunParams): BspResponse[RunResult] = {
    // TODO some task notifications
    val result = RunResult(params.originId, StatusCode.Ok)
    Task(Right(result))
  }

  // for easy override of individual parts of responses

  def name = "BSP Mock Server"
  def serverVersion = "1.0"
  def bspVersion = "2.0"

  def supportedLanguages = List("java","scala")

  def capabilities = bsp.BuildServerCapabilities(
    compileProvider = Some(bsp.CompileProvider(supportedLanguages)),
    testProvider = Some(bsp.TestProvider(supportedLanguages)),
    runProvider = Some(bsp.RunProvider(supportedLanguages)),
    inverseSourcesProvider = Some(true),
    dependencySourcesProvider = Some(true),
    resourcesProvider = Some(true),
    buildTargetChangedProvider = Some(true)
  )

  val baseUri: URI = base.getCanonicalFile.toURI
  val target1 = BuildTargetIdentifier(Uri(baseUri.resolve("target1")))
  val target2 = BuildTargetIdentifier(Uri(baseUri.resolve("target2")))
  val target3 = BuildTargetIdentifier(Uri(baseUri.resolve("target3")))

  def uriInTarget(target: BuildTargetIdentifier, filePath: String): Uri =
    Uri(target1.uri.toPath.toUri.resolve(filePath))

}
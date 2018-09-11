---
id: bsp
title: Build Server Protocol
---

# Build Server Protocol

This document describes version 1.0 of the build server protocol.

Edits to this specification can be made via a pull request against this markdown document.

## Motivation

The problem this document aims to address is the multiplied effort required to integrate between
available language servers/editors and build tools. Currently, every language server must implement
a custom integration for each supported build tool in order to extract compilation information such
as classpaths, source directories or compiler diagnostics. Likewise, new build tools are expected to
integrate with all available IDEs. The growing number of language servers and build tools in the
wider programming community means tooling developers spend a lot of time working on these
integrations.

The Build Server Protocol aims to define common functionality that both build tools (servers) and
language servers/editors (clients) understand. This common functionality enables tooling developers
to provide their end users the best developer experience while supporting build tools and language
servers with less effort and time.


1. [Build Server Protocol](#build-server-protocol)
    1. [Motivation](#motivation)
    2. [Background](#background)
    3. [Status](#status)
    4. [Base protocol](#base-protocol)
    5. [Basic Json Structures](#basic-json-structures)
        1. [Build Target](#build-target)
        2. [Build Target Identifier](#build-target-identifier)
        3. [Task Id](#task-id)
        4. [Uri](#uri)
    6. [Actual Protocol](#actual-protocol)
        1. [Server Lifetime](#server-lifetime)
            1. [Initialize Build Request](#initialize-build-request)
            2. [Initialized Build Notification](#initialized-build-notification)
            3. [Shutdown Build Request](#shutdown-build-request)
            4. [Exit Build Notification](#exit-build-notification)
            5. [Show message](#show-message)
            6. [Log message](#log-message)
            7. [Publish Diagnostics](#publish-diagnostics)
        2. [Workspace Build Targets Request](#workspace-build-targets-request)
        3. [Register File Watcher Request](#register-file-watcher-request)
        4. [Cancel File Watcher Request](#cancel-file-watcher-request)
        5. [Build Target Changed Notification](#build-target-changed-notification)
        6. [Build Target Text Documents Request](#build-target-text-documents-request)
        7. [Text Document Build Targets Request](#text-document-build-targets-request)
        8. [Dependency Sources Request](#dependency-sources-request)
        9. [Resources Request](#resources-request)
        10. [Compile Request](#compile-request)
        11. [Test Request](#test-request)
        12. [Run Request](#run-request)
    7. [Extensions](#extensions)
        1. [Scala](#scala)
            1. [Scala Build Target](#scala-build-target)
            2. [Scalac Options Request](#scalac-options-request)
            3. [Scala Test Classes Request](#scala-test-classes-request)
            4. [Scala Main Classes Request](#scala-main-classes-request)
        2. [Sbt](#sbt)
    8. [Appendix](#appendix)
        1. [Scala Bindings](#scala-bindings)
        2. [FAQ](#faq)

## Background

The Build Server Protocol takes inspiration from the Language Server Protocol (LSP).
Unlike in the Language Server Protocol, the language server or editor is referred to as the “client” and a build tool such as sbt/pants/gradle/bazel is referred to as the “server”.

The best way to read this document is by considering it as a wishlist from the perspective of an IDE
developer.

The code listings in this document are written using Scala syntax.
Every data strucuture in this document has a direct translation to JSON and Protobuf.
See [Appendix](#15-appendix) for schema definitions that can be used to automatically generate
bindings for different target languages.


## Status

The Build Server Protocol is not an approved standard. Everything in this
document is subject to change and open for discussions, including core data
structures.

The creation of BSP clients and servers is under active development.

In the clients space, IntelliJ has been the first language server to implement BSP. The integration
is available in the nightly releases of the Scala plugin. Other language servers, like [Dotty
IDE](https://github.com/lampepfl/dotty) and [scalameta/metals](https://github.com/scalameta/metals),
are currently working or planning to work on a BSP integrations.

On the server side, [bloop](https://github.com/scalacenter/bloop) is the
first server to implement BSP. There are ongoing efforts to implement BSP
in popular build tools like [sbt](https://github.com/sbt/sbt/issues/3890).

We're looking for third parties that implement BSP natively in other build tools like Gradle, Bazel
or Pants.

The Build Server Protocol has been designed to be language-agnostic. We're looking for ways to
collaborate with other programming language communities and build tool authors.

The best way to share your thoughts on the Build Server Protocol or to get involved in its
development is to open an issue or pull request to this repository. Any help on developing
integrations will be much appreciated.

## Base protocol

The base protocol is identical to the language server base protocol. See
<https://microsoft.github.io/language-server-protocol/specification> for
more details.

Like the language server protocol, the build server protocol defines a
set of JSON-RPC request, response and notification messages which are
exchanged using the base protocol.

## Basic Json Structures

In addition to basic data structures in the [General section of the Language Server
Protocol](https://microsoft.github.io/language-server-protocol/specification#general), the Build
Server Protocol defines the following additional data structures.

### Build Target

Build target contains metadata about an artifact (for example library, test, or binary artifact).
Using vocabulary of other build tools:

* sbt: a build target is a combined project + config. Example:
  * a regular JVM project with main and test configurations will have
    2 build targets, one for main and one for test.
  * a single configuration in a single project that contains both Java and Scala sources maps to one BuildTarget.
  * a project with crossScalaVersions 2.11 and 2.12 containing main and test configuration in each will have 4 build targets.
  * a Scala 2.11 and 2.12 cross-built project for Scala.js and the JVM with main and test configurations will have 8 build targets.
* pants: a pants target corresponds one-to-one with a BuildTarget
* bazel: a bazel target corresponds one-to-one with a BuildTarget

The general idea is that the BuildTarget data structure should contain only information that is is fast or cheap to compute.

```scala
trait BuildTarget {

  /** The target’s unique identifier */
  def id: BuildTargetIdentifier

  /** A human readable name for this target.
    * May be presented in the user interface.
    * Should be unique if possible.
    * The id.uri is used if None. */
  def displayName: Option[String]

  /** The type of build target. Useful for an IDE to show
    * targets with different kinds in the UI. */
  def kind: Int
  
  /** The capabilities of this build target. */
  def capabilities: BuildTargetCapabilities

  /** The set of languages that this target contains.
    * The ID string for each language is defined in the LSP. */
  def languageIds: List[String]

  /** Language-specific metadata about this target.
    * See ScalaBuildTarget as an example. */
  def data: Option[Json] // Note, matches `any` in the LSP.
}

object BuildTargetKind {
  final val Library = 1
  final val Test = 2
  final val App = 3
  final val IntegrationTest = 4
  final val Bench = 5
}

trait BuildTargetCapabilities {
  /** This target can be compiled by the BSP server. */
  def canCompile: Boolean
  /** This target can be tested by the BSP server. */
  def canTest: Boolean
  /** This target can be run by the BSP server. */
  def canRun: Boolean
}
```

### Build Target Identifier

A unique identifier for a target, usually the project base directory.

```scala
trait BuildTargetIdentifer {
  /** The target’s Uri */
  def uri: Uri
}
```

### Task Id 

The task Id allows clients to uniquely identify a resource and establish a client-parent
relationship with another id.

```scala
trait TaskId {
  /** The id */
  def id: String

  /** The parent id. */
  def parent: Option[String]
}
```

A task id can represent any child-parent relationship established by the build tool.

An example of use of task ids is logs, where BSP clients can use the task ids of logs
to improve their readability in the user interface. Clients can show logs in a tree fashion, for
example, or with dropdowns.

### Uri

```scala
/**  A resource identifier that is a valid URI according
  * to rfc3986: * https://tools.ietf.org/html/rfc3986 */
type Uri = String
```

## Actual Protocol

Unlike the language server protocol, the build server protocol does not
support dynamic registration of capabilities.
The motivation for this change is simplicity.
If a motivating example for dynamic registration comes up this decision can be reconsidered.
The server and client capabilities must be communicated through the initialize request.

### Server Lifetime

Like the language server protocol, the current protocol specification
defines that the lifetime of a build server is managed by the client
(e.g. a language server like Dotty IDE). It is up to the client to
decide when to start (process-wise) and when to shutdown a server.

#### Initialize Build Request

Like the language server protocol, the initialize request is sent as the
first request from the client to the server. If the server receives a
request or notification before the initialize request it should act as
follows:

* For a request the response should be an error with code: -32002.
  The message can be picked by the server.

* Notifications should be dropped, except for the exit notification.
  This will allow the exit of a server without an initialize
  request.

Until the server has responded to the initialize request with an
InitializeBuildResult, the client must not send any additional requests
or notifications to the server.

Request:

* method: ‘build/initialize’

* params: InitializeBuildParams defined as follows

```scala
trait InitializeBuildParams {

  /** The rootUri of the workspace */
  def rootUri: Uri

  /** The capabilities of the client */
  def capabilities: BuildClientCapabilities

}

trait BuildClientCapabilities {
  /** The languages that this client supports.
    * The ID strings for each language is defined in the LSP.
    * The server must never respond with build targets for other
    * languages than those that appear in this list. */
  def languageIds: List[String]
  
  /** The client can provide support for the file watching
    * notifications to the server. A language server can forward
    * these notifications from the editor. */
  def providesFileWatching: Boolean
}
```

Response:

* result: InitializeBuildResult defined as follows

```scala
trait InitializeBuildResult {
  /** The capabilities of the build server */
  def capabilities: BuildServerCapabilities
}

trait BuildServerCapabilities {
  /** The languages the server supports compilation via method buildTarget/compile. */
  def compileProvider: {
    def languageIds: List[String]
  }
  
  /** The languages the server supports test execution via method buildTarget/test */
  def testProvider: {
    def languageIds: List[String]
  }
  
  /** The languages the server supports run via method buildTarget/run */
  def runProvider: {
    def languageIds: List[String]
  }
  
  /** The server can provide a list of targets that contain a
    * single text document via the method textDocument/buildTargets */
  def providesTextDocumentBuildTargets: Boolean
  
  /** The server provides sources for library dependencies
    * via method buildTarget/dependencySources */
  def providesDependencySources: Boolean
  
  /** The server provides all the resource dependencies
    * via method buildTarget/resources */
  def providesResources: Boolean
  
  /** The server sends notifications to the client on build
    * target change events via buildTarget/didChange */
  def providesBuildTargetChanged: Boolean
}
```

Clients can use these capabilities to notify users what BSP endpoints can
and cannot be used and why.

#### Initialized Build Notification

Like the language server protocol, the initialized notification is sent
from the client to the server after the client received the result of
the initialize request but before the client is sending any other
request or notification to the server. The server can use the
initialized notification for example to initialize intensive computation
such as dependency resolution or compilation. The initialized
notification may only be sent once.

Notification:

* method: ‘build/initialized’
* params: InitializedBuildParams defined as follows

```scala
trait InitializedBuildParams {

}
```

#### Shutdown Build Request

Like the language server protocol, the shutdown build request is sent
from the client to the server. It asks the server to shut down, but to
not exit (otherwise the response might not be delivered correctly to the
client). There is a separate exit notification that asks the server to
exit.

Request:

* method: `build/shutdown`
* params: `null`

Response:

* result: `null`
* error: code and message set in case an exception happens during
  shutdown request.

#### Exit Build Notification

Like the language server protocol, a notification to ask the server to
exit its process. The server should exit with success code 0 if the
shutdown request has been received before; otherwise with error code 1.

Notification:

* method: `build/exit`
* params: `null`

#### Show message

The show message notification is sent from a server to a client to ask the
client to display a particular message in the user interface.

Notification:

* method: `build/showMessage`
* params: `ShowMessageParams` defined as follows:

```scala
trait ShowMessageParams {
  /** The message type. See {@link MessageType}. */
  def type: Int

  /** The task id if any. */
  def task: Option[TaskId]

  /** The request id that originated this notification. */
  def originId: Option[String]

  /** The actual message. */
  def message: String
}
```

where `MessageType` is defined as follows:

```scala
object MessageType {
  /** An error message. */
  final val Error = 1
  /** A warning message. */
  final val Warning = 2
  /** An information message. */
  final val Info = 3
  /** A log message. */
  final val Log = 4
}
```

A `build/showMessage` notification is similar to LSP's `window/showMessage`, except for a few
additions like `id` and `originId`.

The `originId` field helps clients know which request originated a notification in case several
requests are handled by the client at the same time. It will only be populated if the client
defined it in the request that triggered this notification.

#### Log message

The log message notification is sent from the server to the client to ask the
client to log a particular message.

Notification:

* method: ‘build/logMessage’
* params: LogMessageParams defined as follows:

```scala
trait LogMessageParams {
  /** The message type. See {@link MessageType} */
  def type: Int
  
  /** The task id if any. */
  def task: Option[TaskId]
  
  /** The request id that originated this notification. */
  def originId: Option[String]

  /** The actual message */
  def message: String
}
```

Where type is defined as `build/showMessage`.

A `build/logMessage` notification is similar to LSP's `window/logMessage`, except for a few
additions like `id` and `originId`.

The `originId` field helps clients know which request originated a notification in case several
requests are handled by the client at the same time. It will only be populated if the client
defined it in the request that triggered this notification.

#### Publish Diagnostics

The Diagnostics notification are sent from the server to the client to signal results of validation
runs.

Unlike the language server protocol, diagnostics are “owned” by the client so it is the client's
responsibility to manage their lifetime and clear them if necessary. Clients generate new
diagnostics by calling `buildTarget/compile`.

Notification:

* method: `build/publishDiagnostics`
* params: `PublishDiagnosticsParams` defined as follows:

```scala
trait PublishDiagnosticsParams {
  /** The uri of the document where diagnostics are published. */
  def uri: Uri
  
  /** The request id that originated this notification. */
  def originId: Option[String]
  
  /** The diagnostics to be published by the client. */
  def diagnostics: List[Diagnostic]
}
```

where `Diagnostic` is defined as it is in LSP.

The definition of `PublishDiagnosticsParams` is similar to LSP's but contains the addition of an
optional `originId` field. Clients can use this id to know which request originated the
notification. This field will be defined if the client defined it in the original request that
triggered this notification.

### Workspace Build Targets Request

The workspace build targets request is sent from the client to the server to ask for the list of all available build targets in the workspace.

Request:

* method: 'workspace/buildTargets'
* params: `WorkspaceBuildTargetsParams`, defined as follows

```scala
trait WorkspaceBuildTargetsParams {
}
```

Response:

* result: `WorkspaceBuildTargetsResult`, defined as follows

```scala
trait WorkspaceBuildTargetsResult {
  /** The build targets in this workspace that
    * contain sources with the given language ids. */
  def targets: List[BuildTarget]
}
```

### Register File Watcher Request

The file watching request is sent from the server to the client to notify that the server is
interested in watching file changes coming from the client (the editor, or a language server
forwarding `workspace/didChangeWatchedFiles`). These file changes can be used to invalidate tasks
(like compilation) or caches in the build tool.

For a motivation of this feature, check [the LSP `workspace/didChangedWatchedFiles`
description](https://microsoft.github.io/language-server-protocol/specification#workspace_didChangeWatchedFiles).

Request:

* method: `build/registerFileWatcher`
* params: `RegisterFileWatcherParams`, defined as follows

```scala
trait RegisterFileWatcherParams {
   /** The glob pattern to watch in all the workspace. */
   def globPattern: String
   
   /** The kind of events of interest. If omitted, all
     * events will be watched. @link WatchKind */
   def kind: Option[Int]
}

object WatchKind {
  /** Interested in create events. */
  val Create = 1
  /** Interested in change events. */
  val Change = 2
  /** Interested in delete events. */
  val Delete = 3
}
```

The glob pattern to watch in the workspace has the following descriptors:

* `*` Matches 0 or more characters in a single path portion
* `**` matches zero or more path portions
* `*.{scala,js}` expands to two patterns: `*.scala` and `*.js`
* Forward slashes `/` are always treated as path separators, even on Windows.

Further details are implementation defined.

An example of a glob patttern is `src/**/*.{scala,js}`, which describes any javascript or scala file
under the `src/` directory.

Response:

* result: `RegisterFileWatcherResult`, defined as follows

```scala
trait RegisterFileWatcherResult {
  /** The id associated with the watching request. */
  def id: String
}
```

When the watcher is registered, the client can send any number of `build/didChangedWatcheFiles` to
the server.

Notification:

* method: `build/didChangeWatchedFiles`
* params: `DidChangeWatchedFilesParams` defined as follows:

```scala
trait DidChangeWatchedFiles {
  /** The actual file events. */
  def changes: List[FileEvent]
}
```

Where `FileEvent`s are described as follows:

```scala
trait FileEvent {
  /** The file's URI. */
  def uri: Uri

  /** The kind of file event. @link FileChangeType */
  def type: Int
}

object FileChangeType {
  /** The file was just created. */
  val Created = 1
  /** The file was just changed. */
  val Changed = 2
  /** The file was just deleted. */
  val Deleted = 3
}
```

### Cancel File Watcher Request

A cancel file watcher request is sent from the server to the client to cancel file watching events.
The cancellation is done using the `id` of returned by `RegisterFileWatcherResult`.

Request:

* method: `build/cancelFileWatcher`
* params: `CancelFileWatcherParams`, defined as follows

```scala
trait CancelFileWatcherParams {
  /** The identifier of the registered file watcher. */
  def id: String
}
```

Response:

* result: `CancelFileWatcherResult` defined as follows

```scala
trait CancelFileWatcherResult {
  /** Whether file watching was cancelled. */
  def cancelled: Boolean
}
```

### Build Target Changed Notification

The build target changed notification is sent from the server to the client to signal a change in a build target.
The server communicates during the initialize handshake whether this method is supported or not.

Notification:

* method: 'buildTarget/didChange'
* params: `DidChangeBuildTargetParams` defined as follows:

```scala
trait DidChangeBuildTarget {
  def changes: List[BuildTargetEvent]
}

trait BuildTargetEvent {
  /** The identifier for the changed build target */
  def uri: Uri
  
  /** The kind of change for this build target */
  def kind: Option[Int]

  /** Any additional metadata about what information changed. */
  def data: Option[Json]
}
```

where the `kind` is defined as follows:

```scala
object BuildTargetEventKind {
  /** The build target is new. */
  val Created = 1
  
  /** The build target has changed. */
  val Changed = 2
  
  /** The build target has been deleted. */
  val Deleted = 3
}
```

The `BuildTargetEventKind` information can be used by clients to trigger reindexing or update the
user interface with the new information.

### Build Target Text Documents Request

The build target text documents request is sent from the client to the server to query for the list of source files that are part of a given list of build targets.

* method: `buildTarget/textDocuments`
* params: `BuildTargetTextDocumentsParams`

```scala
trait BuildTargetTextDocumentsParams {
  def targets: List[BuildTargetIdentifier]
}
```

Response:

* result: `BuildTargetTextDocumentsResult`, defined as follows

```scala
trait BuildTargetTextDocumentsResponseResult {
  /** The source files used by this target */
  def textDocuments: List[TextDocumentIdentifier]
}
```

### Text Document Build Targets Request

The text document build targets request is sent from the client to the server to query for the list of targets containing the given text document.
The server communicates during the initialize handshake whether this method is supported or not.

This request may be considered as the inverse of `buildTarget/textDocuments`.
This method can be used by a language server on `textDocument/didOpen` to lookup which compiler instance to use to compile that given text document.
In the case there are multiple targets (for example different platforms: JVM/JS, or x86/ARM) containing the same source file, the language server may present in the editor multiple options via `textDocument/codeLens` to configure how to dis-ambiguate.

* method: `textDocument/buildTargets`
* params: `TextDocumentBuildTargetsParams`, defined as follows

```scala
trait TextDocumentBuildTargetsParams {
  def textDocument: TextDocumentIdentifier
}
```

Response:

* result: `TextDocumentBuildTargetsResult`, defined as follows

```scala
trait TextDocumentBuildTargetsResult {
  def targets: List[BuildTargetIdentifier]
}
```

### Dependency Sources Request

The build target dependency sources request is sent from the client to the server to query for the list of sources for the dependency classpath of a given list of build targets.
The server communicates during the initialize handshake whether this method is supported or not.
This method can be used by a language server on `textDocument/definition` to "Go to definition" from project sources to dependency sources.

* method: `buildTarget/dependencySources`
* params: `DependencySourcesParams`

```scala
trait DependencySourcesParams {
  def targets: List[BuildTargetIdentifier]
}
```

Response:

* result: `DependencySourcesResult`, defined as follows

```scala
trait DependencySourcesResult {
  def items: List[DependencySourcesItem]
}
trait DependencySourcesItem {
  def target: BuildTargetIdentifier
  /** List of resources containing source files of the
    * target's dependencies.
    * Can be source files, jar files, zip files, or directories. */
  def sources: List[Uri]
}
```

### Resources Request

The build target resources request is sent from the client to the server to query for the list of
resources of a given list of build targets.

A resource is a data dependency required to be present in the runtime classpath when a build target
is run or executed. The server communicates during the initialize handshake whether this method is
supported or not.

This request can be used by a client to highlight the resources in a project view, for example.

* method: `buildTarget/resources`
* params: `ResourcesParams`

```scala
trait ResourcesParams {
  def targets: List[BuildTargetIdentifier]
}
```

Response:

* result: `ResourcesResult`, defined as follows

```scala
trait ResourcesResult {
  def items: List[ResourcesItem]
}
trait ResourcesItem {
  def target: BuildTargetIdentifier
  /** List of resource files. */
  def resources: List[Uri]
}
```

### Compile Request

The compile build target request is sent from the client to the server to compile the given list of build targets.
The server communicates during the initialize handshake whether this method is supported or not.
This method can for example be used by a language server before `textDocument/rename` to ensure that all workspace sources typecheck correctly and are up-to-date.

* method: `buildTarget/compile`
* params: `CompileParams`

```scala
trait CompileParams {
  /** A sequence of build targets to compile. */
  def targets: List[BuildTargetIdentifier]
  
  /** A unique identifier generated by the client to identify this request.
    * The server may include this id in triggered notifications or responses. */
  def originId: Option[String]

  /** Optional arguments to the compilation process. */
  def arguments: List[Json]
}
```

Response:

* result: `CompileResult`, defined as follows
* error: JSON-RPC code and message set in case an exception happens during the request.

```scala
trait CompileResult {
  /** An optional request id to know the origin of this report. */
  def originId: Option[String]
  /** A field containing language-specific information, like products
    * of compilation or compiler-specific metadata the client needs to know. */
  def data: Option[Json] // Note, matches `any | null` in the LSP.
  
}
```

Notification:

* method: `buildTarget/compileReport`
* params: `CompileReport` defined as follows:

```scala
trait CompileReport {
  /** The build target that was compiled. */
  def target: BuildTargetIdentifier
  
  /** An optional request id to know the origin of this report. */
  def originId: Option[String]

  /** The total number of reported errors compiling this target. */
  def errors: Int

  /** The total number of reported warnings compiling the target. */
  def warnings: Int

  /** The total number of milliseconds it took to compile the target. */
  def time: Option[Int]
}
```

The server is free to send any number of `build/publishDiagnostics` and `build/logMessage`
notifications during compilation before completing the response. The client is free to forward these
messages to the LSP editor client.

The client will get a `originId` field in `CompileReport` or `CompileResult` if the `originId`
field in the `CompileParams` is defined.

### Test Request

The test build target request is sent from the client to the server to test the given list of build targets.
The server communicates during the initialize handshake whether this method is supported or not.

* method: `buildTarget/test`
* params: `TestParams`

```scala
trait TestParams {
  /** A sequence of build targets to test. */
  def targets: List[BuildTargetIdentifier]
  
  /** A unique identifier generated by the client to identify this request.
    * The server may include this id in triggered notifications or responses. */
  def originId: Option[String]
  
  /** Optional arguments to the test execution. */
  def arguments: List[Json]
}
```

Response:

* result: `TestResult`, defined as follows
* error: JSON-RPC code and message set in case an exception happens during the request.

```scala
trait TestResult {
  def data: Option[Json] // Note, matches `any | null` in the LSP.
    
  /** An optional request id to know the origin of this report. */
  def originId: Option[String]
}
```

The field `data` may contain test-related language-specific information.

Notification:

* method: `buildTarget/testReport`
* params: `TestReport` defined as follows:

```scala
trait TestReport {
  /** The build target that was compiled. */
  def target: BuildTargetIdentifier
  
  /** An optional request id to know the origin of this report. */
  def originId: Option[String]
  
  /** The total number of successful tests. */
  def passed: Int

  /** The total number of failed tests. */
  def failed: Int
  
  /** The total number of ignored tests. */
  def ignored: Int

  /** The total number of cancelled tests. */
  def cancelled: Int

  /** The total number of skipped tests. */
  def skipped: Int

  /** The total number of milliseconds tests take to run (e.g. doesn't include compile times). */
  def time: Option[Int]
}
```

The `TestReport` notification will be sent as the test execution of build targets completes.

This request may trigger a compilation on the selected build targets. The server is free to send any
number of `build/compileReport`, `build/publishDiagnostics` and `build/logMessage` notifications
during compilation before completing the response.

The client will get a `originId` field in the `TestReport` or `TestResult` if the `originId` field
in the `TestParams` is defined.

### Run Request

The run request is sent from the client to the server to run a build target. The server communicates
during the initialize handshake whether this method is supported or not.

* method: `buildTarget/run`
* params: `RunParams`

```scala
trait RunParams {
  /** The build target to run. */
  def target: BuildTargetIdentifier
  
  /** A unique identifier generated by the client to identify this request.
    * The server may include this id in triggered notifications or responses. */
  def originId: Option[String]
  
  /** Optional arguments to the test execution. */
  def arguments: Option[Json]
}
```

Note that an empty run request is valid. Run will be executed in the target as specified in the
build tool.

Response:

* result: `RunResult`, defined as follows
* error: JSON-RPC code and message set in case an exception happens during the request.

```scala
trait RunResult {
  /** An optional request id to know the origin of this report. */
  def originId: Option[String] 
  
  /** A status code for the execution. */
  def statusCode: Int
}

object StatusCode {
  /** Execution was successful. */
  val Ok = 1
  /** Execution failed. */
  val Error = 2
  /** Execution was cancelled. */
  val Cancelled = 2
}

```

This request may trigger a compilation on the selected build targets. The server is free to send any
number of `build/compileReport`, `build/publishDiagnostics` and `build/logMessage` notifications
during compilation before completing the response.

The client will get a `originId` field in `RunResult` if the `originId` field in the
`RunParams` is defined.

## Extensions

The build server protocol is designed to be extended with language specific data structures and methods.

### Scala

The following section contains Scala-specific extensions to the build server protocol.

#### Scala Build Target

`ScalaBuildTarget` is a basic data structure that contains scala-specific metadata for compiling a target containing Scala sources.
This metadata is embedded in the `data: Option[Json]` field of the `BuildTarget` definition.

```scala
trait ScalaBuildTarget {
  /** The Scala organization that is used for a target. */
  def scalaOrganization: String

  /** The scala version to compile this target */
  def scalaVersion: String

  /** The binary version of scalaVersion.
    * For example, 2.12 if scalaVersion is 2.12.4. */
  def scalaBinaryVersion: String

  /** The target platform for this target */
  def platform: Int
  
  /** A sequence of Scala jars. */
  def scalaJars: List[String]
}

object ScalaPlatform {
  val JVM = 1
  val JS = 2
  val Native = 3
}
```

#### Scalac Options Request

The build target scalac options request is sent from the client to the server to query for the list of compiler options necessary to compile in a given list of targets.

* method: `buildTarget/scalacOptions`
* params: `ScalacOptionsParams`

```scala
trait ScalacOptionsParams {
  def targets: List[BuildTargetIdentifier]
}
```

Response:

* result: `ScalacOptionsResult`, defined as follows

```scala
trait ScalacOptionsResult {
  def items: List[ScalacOptionsItem]
}

trait ScalacOptionsItem {
    def target: BuildTargetIdentifier

    /** Additional arguments to the compiler.
      * For example, -deprecation. */
    def options: List[String]
    
    /** The dependency classpath for this target, must be
      * identical to what is passed as arguments to
      * the -classpath flag in the command line interface
      * of scalac. */
    def classpath: List[Uri]
    
    /** The output directory for classfiles produced by this target */
    def classDirectory: Uri
}
```

#### Scala Test Classes Request

The build target scala test options request is sent from the client to the server to query for the
list of fully qualified names of test clases in a given list of targets.

This method can for example be used by a client to:
  
* Show a list of the discovered classes that can be tested.
* Attach a "Run test suite" button above the definition of a test suite via `textDocument/codeLens`.

(To render the code lens, the language server needs to map the fully qualified names of the test
targets to the defining source file via `textDocument/definition`. Then, once users click on the
button, the language server can pass the fully qualified name of the test class as an argument to
the `buildTarget/test` request.)

* method: `buildTarget/scalaTestClasses`
* params: `ScalaTestClassesParams`

```scala
trait ScalaTestClassesParams {
  def targets: List[BuildTargetIdentifier]
  
  /** An optional number uniquely identifying a client request. */
  def originId: Option[String]
}
```

Response:

* result: `ScalaTestClassesResult`, defined as follows
* error: code and message set in case an exception happens during
  shutdown request.

```scala
trait ScalaTestClassesResult {
  def items: List[ScalaTestClassesItem]
  
  /** An optional id of the request that triggered this result. */
  def originId: Option[String]

}

trait ScalaTestClassesItem {
  /** The build target that contains the test classes. */
  def target: BuildTargetIdentifier

  /** The fully qualified names of the test classes in this target */
  def classes: List[String]
}
```

This request may trigger a compilation on the selected build targets. The server is free to send any
number of `build/compileReport`, `build/publishDiagnostics` and `build/logMessage` notifications
during compilation before completing the response.

The client will get a `originId` field in `ScalaTestClassesResult` if the `originId` field in the
`ScalaTestClassesParams` is defined.

#### Scala Main Classes Request

The build target main classes request is sent from the client to the server to query for the list of
main classes that can be fed as arguments to `buildTarget/run`. This method can be used for the same
use cases than the [Scala Test Classes Request](#1.7.1.3.-scala-test-classes-request) enables.

* method: `buildTarget/scalaMainClasses`
* params: `ScalaMainClassesParams`

```scala
trait ScalaMainClassesParams {
  def targets: List[BuildTargetIdentifier]
  
  /** An optional number uniquely identifying a client request. */
  def originId: Option[String]
}
```

Response:

* result: `ScalaMainClassesResult`, defined as follows
* error: code and message set in case an exception happens during shutdown request.

```scala
trait ScalaMainClassesResult {
  def items: List[ScalaMainClassesItem]
  
  /** An optional id of the request that triggered this result. */
  def originId: Option[String]

}

trait ScalaMainClassesItem {
  /** The build target that contains the test classes. */
  def target: BuildTargetIdentifier

  /** The main class item. */
  def mainClass: ScalaMainClass
}

trait ScalaMainClass {
  /** The main class to run. */
  def class: String

  /** The user arguments to the main entrypoint. */
  def arguments: List[String]
  
  /** The jvm options for the application. */
  def jvmOptions: List[String]
}

```

This request may trigger a compilation on the selected build targets. The server is free to send any
number of `build/compileReport`, `build/publishDiagnostics` and `build/logMessage` notifications
during compilation before completing the response.

The client will get a `originId` field in `ScalaMainClassesResult` if the `originId` field in the
`ScalaMainClassesParams` is defined.

### Sbt

The following section contains sbt-specific extensions to the build server protocol. This extension
allows BSP clients to provide language support for sbt build files.

`SbtBuildTarget` is a basic data structure that contains sbt-specific metadata for providing editor
support for sbt build files. This metadata is embedded in the `data: Option[Json]` field of the
`BuildTarget` definition.

```scala
trait SbtBuildTarget {
  /** The sbt version. Useful to support version-dependent syntax. */
  def sbtVersion: String
  
  /** A sequence of Scala imports that are automatically imported in the sbt build files. */
  def autoImports: List[String]
  
  /** The classpath for the sbt build (including sbt jars). */
  def classpath: List[Uri]
  
  /** The Scala build target describing the scala
   * version and scala jars used by this sbt version. */
  def scalaBuildTarget: ScalaBuildTarget
  
  /** An optional parent if the target has an sbt meta project. */
  def parent: Option[BuildTargetIdentifier]
  
  /** The inverse of parent, list of targets that have this build target
    * defined as their parent. It can contain normal project targets or
    * sbt build targets if this target represents an sbt meta-meta build. */
  def children: List[BuildTargetIdentifier]
}
```

For example, say we have a project in `/foo/bar` defining projects `A` and `B` and two meta builds
`M1` (defined in `/foo/bar/project`) and `M2` (defined in `/foo/bar/project/project`).

The sbt build target for `M1` will have `A` and `B` as the defined targets and `M2` as the parent.
Similarly, the sbt build target for `M2` will have `M1` as the defined target and no parent.

Clients can use this information to reconstruct the tree of sbt meta builds. The
`parent` information can be defined from `children` but it's provided by the server to
simplify the data processing on the client side.

## Appendix

### Scala Bindings

A Scala library implementation of this communication protocol is available in this repository.
The public API of this library currently has three direct Scala dependencies:

* Scribe - for logging
* Monix - for asynchronous programming primitives
* Circe - for JSON serialization and parsing of protocol data structures

### FAQ

**Q**: What's the relationship between BSP and LSP?

**A**: They are complementary protocols.
While LSP specifies endpoints for communication between an **editor acting as client** and language server, BSP specifies endpoints between a **language server acting as client** and build server.
For example, in order to respond to a `textDocument/definition` request from an editor client, a language server could query a build tool via BSP for the classpath of a module.

**Q**: What's the relationship between implementations of BSP and implementations of LSP like
[dragos/dragos-vscode-scala](https://github.com/dragos/dragos-vscode-scala),
[Dotty IDE](https://marketplace.visualstudio.com/items?itemName=lampepfl.dotty) or
[Metals](https://github.com/scalameta/metals)?

**A**: Currently, those language servers each implement custom integrations for each supported build tool to extract build metadata.
Those language servers could instead implement a BSP client to extract build metadata from any build tools that implement BSP, sharing a single BSP server implementation.
Likewise, a new build tool could implement a BSP server and support a wide range of language servers out-of-the-box.

**Q**: Should non-Scala participants in the protocol generate data types from `bsp.proto` or is it preferable to use pre-generated artifacts in maven (or other repos)?

**A**: BSP uses JSON on the wire like LSP, it is not necessary to use `bsp.proto`.
The `bsp.proto` schema is provided as a language-agnostic reference schema for the shape of BSP data structures, similarly to how LSP messages are defined using TypeScript interfaces.
Like with LSP, it is left to BSP participant to figure out how to produce JSON payloads with BSP data structures.

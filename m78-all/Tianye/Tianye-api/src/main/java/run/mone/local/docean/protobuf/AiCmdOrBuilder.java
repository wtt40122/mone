// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: bo.proto

package run.mone.local.docean.protobuf;

public interface AiCmdOrBuilder extends
    // @@protoc_insertion_point(interface_extends:run.mone.local.docean.protobuf.AiCmd)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string cmd = 1;</code>
   * @return The cmd.
   */
  java.lang.String getCmd();
  /**
   * <code>string cmd = 1;</code>
   * @return The bytes for cmd.
   */
  com.google.protobuf.ByteString
      getCmdBytes();

  /**
   * <code>map&lt;string, string&gt; cmdMeta = 2;</code>
   */
  int getCmdMetaCount();
  /**
   * <code>map&lt;string, string&gt; cmdMeta = 2;</code>
   */
  boolean containsCmdMeta(
      java.lang.String key);
  /**
   * Use {@link #getCmdMetaMap()} instead.
   */
  @java.lang.Deprecated
  java.util.Map<java.lang.String, java.lang.String>
  getCmdMeta();
  /**
   * <code>map&lt;string, string&gt; cmdMeta = 2;</code>
   */
  java.util.Map<java.lang.String, java.lang.String>
  getCmdMetaMap();
  /**
   * <code>map&lt;string, string&gt; cmdMeta = 2;</code>
   */

  java.lang.String getCmdMetaOrDefault(
      java.lang.String key,
      java.lang.String defaultValue);
  /**
   * <code>map&lt;string, string&gt; cmdMeta = 2;</code>
   */

  java.lang.String getCmdMetaOrThrow(
      java.lang.String key);
}

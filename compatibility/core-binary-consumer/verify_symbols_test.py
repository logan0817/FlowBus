import hashlib
from pathlib import Path
import tempfile
import unittest
import zipfile

from verify_symbols import classes_jar, compare, parse_members, public_api_members, verify_artifact_hashes


class JvmSymbolVerificationTest(unittest.TestCase):
    def test_descriptor_removal_and_static_change_are_rejected(self):
        old = {"Bus": {("send", "(I)V", False)}}
        self.assertEqual(compare(old, old), [])
        self.assertEqual(compare(old, {"Bus": set()}), [["Bus", "send", "(I)V", False]])
        self.assertEqual(compare(old, {"Bus": {("send", "(I)V", True)}}),
                         [["Bus", "send", "(I)V", False]])
        self.assertIn(["Bus", "<class>"], compare(old, {}))

    def test_defaults_and_bridges_are_preserved(self):
        output = """
public final class Bus
{
  public static final void send$default(Bus, int);
    descriptor: (LBus;I)V
    flags: (0x1009) ACC_PUBLIC, ACC_STATIC, ACC_SYNTHETIC
  public java.lang.Object get();
    descriptor: ()Ljava/lang/Object;
    flags: (0x1041) ACC_PUBLIC, ACC_BRIDGE, ACC_SYNTHETIC
  public static final void access$close(Bus);
    descriptor: (LBus;)V
    flags: (0x1009) ACC_PUBLIC, ACC_STATIC, ACC_SYNTHETIC
  protected Bus();
    descriptor: ()V
    flags: (0x0004) ACC_PROTECTED
}
InnerClasses:
  public static final #35= #32 of #34;    // metadata, not a JVM field
"""
        members, excluded = parse_members(output)
        self.assertEqual(members["Bus"], {
            ("send$default", "(LBus;I)V", True),
            ("get", "()Ljava/lang/Object;", False),
            ("<init>", "()V", False)
        })
        self.assertEqual(excluded, [["Bus", "access$close"]])

    def test_malformed_output_cannot_pass(self):
        with self.assertRaises(ValueError):
            parse_members("unrecognized output")

    def test_aar_reads_only_classes_jar_and_cleans_temporary_file(self):
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "library.aar"
            with zipfile.ZipFile(artifact, "w") as archive:
                archive.writestr("classes.jar", b"compiled library")
                archive.writestr("../unexpected.txt", b"must not extract")
            with classes_jar(artifact) as jar:
                self.assertEqual(jar.read_bytes(), b"compiled library")
                extracted = jar
                self.assertEqual(list(jar.parent.iterdir()), [jar])
            self.assertFalse(extracted.exists())
            self.assertEqual(list(Path(directory).iterdir()), [artifact])

    def test_aar_missing_classes_jar_cannot_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "empty.aar"
            with zipfile.ZipFile(artifact, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"manifest")
            with self.assertRaises(KeyError):
                with classes_jar(artifact):
                    self.fail("Missing classes.jar must not be accepted")

    def test_changed_artifact_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / "library.jar"
            jar.write_bytes(b"first")
            evidence = {}
            for role in ("baseline", "candidate"):
                evidence[role + "Jar"] = str(jar)
                evidence[role + "Sha256"] = hashlib.sha256(jar.read_bytes()).hexdigest()
            verify_artifact_hashes(evidence)
            jar.write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError, "Artifact changed"):
                verify_artifact_hashes(evidence)

    def test_public_api_snapshot_must_match_original_bytecode(self):
        with tempfile.TemporaryDirectory() as directory:
            snapshot = Path(directory) / "library.api"
            snapshot.write_text("""
public final class sample/Bus {
    public fun <init> ()V
    public static final field INSTANCE Lsample/Bus;
    public final fun send (I)V
}
""")
            supported = public_api_members(snapshot)
            original = {"sample.Bus": {
                ("<init>", "()V", False),
                ("INSTANCE", "Lsample/Bus;", True),
                ("send", "(I)V", False),
                ("internal$module", "()V", False)
            }}
            self.assertEqual(compare(supported, original), [])
            self.assertNotEqual(compare(supported, {"sample.Bus": set()}), [])
            candidate = {"sample.Bus": original["sample.Bus"] - {("internal$module", "()V", False)}}
            self.assertEqual(compare(supported, candidate), [])
            self.assertEqual(compare(original, candidate), [["sample.Bus", "internal$module", "()V", False]])


if __name__ == "__main__":
    unittest.main()

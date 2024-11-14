package directory

import (
	"testing"

	"github.com/spf13/afero"
)

func TestFindInstallPathWrongBinaryName(t *testing.T) {
	t.Parallel()
	fs := afero.NewMemMapFs()

	_, err := FindInstallPath(fs, "wrong-binary-name")
	if err == nil {
		t.Fatalf("Expected error")
	}

	expectedError := "expected executable to be named 'launcher' not 'wrong-binary-name'"
	if err.Error() != expectedError {
		t.Fatalf("Expected error %s but got %s", expectedError, err.Error())
	}
}

func TestFindInstallPathWrongBinaryNameFullPath(t *testing.T) {
	t.Parallel()
	fs := afero.NewMemMapFs()

	_, err := FindInstallPath(fs, "some/path/wrong-binary-name")
	if err == nil {
		t.Fatalf("Expected error")
	}

	expectedError := "expected executable to be named 'launcher' not 'wrong-binary-name'"
	if err.Error() != expectedError {
		t.Fatalf("Expected error %s but got %s", expectedError, err.Error())
	}
}

func TestFindInstallCannotDetectBaseDir(t *testing.T) {
	t.Parallel()
	fs := afero.NewMemMapFs()

	_, err := FindInstallPath(fs, LauncherName)
	if err == nil {
		t.Fatalf("Expected error")
	}

	expectedError := "could not detect installation directory"
	if err.Error() != expectedError {
		t.Fatalf("Expected error '%s' but got: '%s'", expectedError, err.Error())
	}
}

func TestFindInstallDetectBaseDir(t *testing.T) {
	t.Parallel()
	fs := afero.NewMemMapFs()
	_, err := fs.Create("/usr/local/bin/launcher")
	if err != nil {
		t.Fatalf("Got unexpected error: %s", err)
	}

	detectedPath, err := FindInstallPath(fs, "/usr/local/bin/something/launcher")
	if err != nil {
		t.Fatalf("Unexpected error: %s", err.Error())
	}

	expectedPath := "/usr/local"
	if detectedPath != expectedPath {
		t.Fatalf("Expected detected path to be '%s' but got: '%s'", expectedPath, detectedPath)
	}
}

func TestDirExists(t *testing.T) {
	t.Parallel()
	fs := afero.NewMemMapFs()
	_, err := fs.Create("/usr/local/bin/launcher")
	if err != nil {
		t.Fatalf("Got unexpected error: %s", err)
	}

	err = DirExists(fs, "/usr/local")
	if err != nil {
		t.Fatalf("Expected /usr/local to exist: %v", err)
	}

	err = DirExists(fs, "/usr/local/bin")
	if err != nil {
		t.Fatalf("Expected /usr/local to exist: %v", err)
	}

	err = DirExists(fs, "/invalid")
	if err == nil {
		t.Fatalf("Expected /invalid NOT to exist: %v", err)
	}
}

func TestEnsureWritable(t *testing.T) {
	t.Parallel()
	fs := afero.NewMemMapFs()

	err := EnsureWritable(fs, "/usr/local/bin")
	if err != nil {
		t.Fatalf("Expected /usr/local/bin to be writable: %v", err)
	}

	fs.Mkdir("/invalid", 0500)
	err = EnsureWritable(fs, "/invalid/foo")
	if err == nil {
		t.Fatalf("Expected /invalid/foo NOT to be writable: %v", err)
	}
}

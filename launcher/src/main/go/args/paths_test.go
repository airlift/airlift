package args

import (
	"testing"

	"github.com/spf13/afero"
)

func TestPathMissing(t *testing.T) {
	t.Parallel()
	fs := afero.NewMemMapFs()

	_, err := ResolvePathAndReadFile(fs, "/invalid1", "/invalid2")

	if err == nil {
		t.Fatalf("expected ResolvePathAndReadFile to return an error")
	}
	expected := "could not find file: /invalid1"
	if err.Error() != expected {
		t.Fatalf("expected ResolvePathAndReadFile to return %s, but got %v", expected, err)
	}
}

func TestPathOk(t *testing.T) {
	t.Parallel()
	fs := afero.NewMemMapFs()

	file, _ := fs.Create("/valid")
	file.WriteString("key=value")
	file.Close()

	actual, err := ResolvePathAndReadFile(fs, "/valid")

	if err != nil {
		t.Fatalf("expected ResolvePathAndReadFile NOT to return an error, got: %v", err)
	}
	expected := []string{"key=value"}
	if !equal(expected, actual) {
		t.Fatalf("expected %v, but got %v", expected, actual)
	}
}

func equal[S ~[]E, E comparable](s1, s2 S) bool {
	if len(s1) != len(s2) {
		return false
	}
	for i := range s1 {
		if s1[i] != s2[i] {
			return false
		}
	}
	return true
}

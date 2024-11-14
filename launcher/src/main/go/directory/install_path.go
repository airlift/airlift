package directory

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"

	"github.com/spf13/afero"
)

const LauncherName = "launcher"
const LauncherScriptName = "launcher"

var ErrNotAFile = errors.New("not a file")
var ErrNotADir = errors.New("not a directory")

// FindInstallPath traverses from binary to parent paths to find install root
func FindInstallPath(fs afero.Fs, executablePath string) (string, error) {
	if filepath.Base(executablePath) != LauncherName {
		return "", fmt.Errorf("expected executable to be named '%s' not '%s'", LauncherName, filepath.Base(executablePath))
	}

	currentDir := filepath.Join(filepath.Dir(executablePath), "..")
	for {
		launcherScript := filepath.Join(currentDir, LauncherScriptName)
		if err := FileExists(fs, launcherScript); err == nil {
			// if we found the launcher script (it goes to bin/ then we need to be one dir higher)
			parentDir, err := filepath.Abs(filepath.Join(currentDir, ".."))
			if err != nil {
				return "", fmt.Errorf("failed to resolve the parent directory of %s: %w", currentDir, err)
			}
			return parentDir, nil
		}
		dir, err := filepath.Abs(filepath.Join(currentDir, ".."))
		if err != nil {
			return "", fmt.Errorf("failed to resolve the parent directory of %s: %w", currentDir, err)
		}
		if dir == string(filepath.Separator) {
			return "", fmt.Errorf("could not detect installation directory")
		}
		currentDir = dir
	}
}

func FileExists(fs afero.Fs, path string) error {
	info, err := fs.Stat(absFile(path))
	if err != nil && os.IsNotExist(err) {
		return err
	}
	if info == nil || info.IsDir() {
		return ErrNotAFile
	}
	return nil
}

func DirExists(fs afero.Fs, path string) error {
	info, err := fs.Stat(absFile(path))
	if err != nil && os.IsNotExist(err) {
		return err
	}
	if info == nil || !info.IsDir() {
		return ErrNotADir
	}
	return nil
}

func absFile(path string) string {
	fullPath := ""
	if filepath.IsAbs(path) {
		return path
	}

	res, err := filepath.Abs(path)
	if err != nil {
		fullPath = filepath.Join("/", path)
	} else {
		fullPath = res
	}
	return fullPath
}

func EnsureWritable(fs afero.Fs, path string) error {
	info, err := fs.Stat(absFile(path))
	if os.IsNotExist(err) {
		dir := filepath.Dir(path)
		err = fs.MkdirAll(dir, 0700)
		if err != nil && !os.IsExist(err) {
			return err
		}
		return Writable(fs, dir)
	}
	if info == nil || info.Mode()&0222 == 0 {
		return os.ErrPermission
	}
	return nil
}

func IsExecOwner(mode os.FileMode) bool {
	return mode&0100 != 0
}

func VerifyJvmInstallation(fs afero.Fs, path string) error {
	if err := DirExists(fs, path); err != nil {
		return err
	}

	jvmExecutable, err := fs.Stat(filepath.Join(path, "bin/java"))
	if errors.Is(err, os.ErrNotExist) {
		return err
	}

	mode := jvmExecutable.Mode()
	if !mode.IsRegular() {
		return ErrNotAFile
	}
	if !IsExecOwner(mode) {
		return os.ErrPermission
	}
	return nil
}

func Writable(fs afero.Fs, path string) error {
	info, err := fs.Stat(absFile(path))
	if os.IsNotExist(err) {
		return Writable(fs, filepath.Dir(path))
	}
	if info == nil || info.Mode()&0222 == 0 {
		return os.ErrPermission
	}
	return nil
}

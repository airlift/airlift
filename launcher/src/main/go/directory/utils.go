package directory

import (
	"fmt"
	"os"
)

// MakeDirs creates directory and all intermediate ones
func MakeDirs(path string) error {
	err := os.MkdirAll(path, os.ModePerm)
	if err != nil {
		if !os.IsExist(err) {
			return err
		}
	}
	return nil
}

// SymlinkExists checks if symlink exists and raise if another type of file exists
func SymlinkExists(path string) (bool, error) {
	stat, err := os.Lstat(path)
	if err != nil {
		if os.IsNotExist(err) {
			return false, nil
		}
		return false, err
	}
	if stat.Mode()&os.ModeSymlink == 0 {
		return false, fmt.Errorf("path %s exists and is not a symlink", path)
	}
	return true, nil
}

// CreateSymlink creates a symlink, removing the target first if it is a symlink
func CreateSymlink(source string, target string) error {
	exists, err := SymlinkExists(target)
	if err != nil {
		return fmt.Errorf("failed to check if symlink exists: %w", err)
	}

	if exists {
		err := os.Remove(target)
		if err != nil {
			return fmt.Errorf("failed to remove existing symlink: %w", err)
		}
	}

	if _, err := os.Stat(source); err == nil {
		err = os.Symlink(source, target)
		if err != nil {
			return fmt.Errorf("failed to create symlink: %w", err)
		}
	}
	return nil
}

// OpenAppend opens a raw file descriptor in append mode
func OpenAppend(path string) (*os.File, error) {
	file, err := os.OpenFile(path, os.O_WRONLY|os.O_APPEND|os.O_CREATE, 0644)
	if err != nil {
		return nil, err
	}
	return file, nil
}

func Exists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}

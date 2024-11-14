package args

import (
	"fmt"
	"launcher/directory"
	"launcher/properties"
	"path/filepath"
	"strings"

	"github.com/spf13/afero"
)

func ResolvePathAndReadFile(fs afero.Fs, searchPaths ...string) ([]string, error) {
	file, err := ResolveFile(fs, searchPaths...)
	if err != nil {
		return nil, err
	}

	return properties.LoadLines(fs, file)
}

func ResolveFile(fs afero.Fs, searchPaths ...string) (string, error) {
	resolved, err := Resolve(fs, directory.FileExists, searchPaths...)
	if err != nil {
		return "", fmt.Errorf("could not find file: %s", firstNonEmpty(searchPaths...))
	}
	return resolved, nil
}

func Resolve(fs afero.Fs, test func(afero.Fs, string) error, searchPaths ...string) (string, error) {
	for _, searchPath := range searchPaths {
		if searchPath == "" {
			continue
		}

		absPath, err := filepath.Abs(searchPath)
		if err != nil {
			return "", err
		}

		if err := test(fs, absPath); err != nil {
			return "", fmt.Errorf("path %s is invalid: %w", absPath, err)
		}

		return absPath, nil
	}

	return "", fmt.Errorf("no search path matches: %s", strings.Join(searchPaths, ", "))
}

func ResolvePath(fs afero.Fs, searchPaths ...string) (string, error) {
	resolved, err := Resolve(fs, directory.DirExists, searchPaths...)
	if err != nil {
		return "", fmt.Errorf("could not find directory: %s", firstNonEmpty(searchPaths...))
	}
	return resolved, nil
}

func firstNonEmpty(searchPaths ...string) string {
	for _, searchPath := range searchPaths {
		if searchPath != "" {
			return searchPath
		}
	}

	return "n/a"
}

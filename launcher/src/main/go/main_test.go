package main

import (
	"os"
	"testing"
)

type mockPidFile struct{}

func (p *mockPidFile) GetPidFile() *os.File {
	return nil
}

func (p *mockPidFile) AcquireLock() error {
	return nil
}

func (p *mockPidFile) ReleaseLock() error {
	return nil
}

func (p *mockPidFile) ClearPid() error {
	return nil
}

func (p *mockPidFile) WritePid(pid int) error {
	return nil
}

func (p *mockPidFile) Alive() bool {
	return true
}

func (p *mockPidFile) ReadPid() (int, error) {
	return 0, nil
}

func TestStatus(t *testing.T) {
	p := &mockPidFile{}

	err := status(p)

	if err != nil {
		t.Fatalf("expected no error but got %v", err)
	}
}

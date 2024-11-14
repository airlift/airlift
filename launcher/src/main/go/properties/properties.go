package properties

import (
	"errors"
	"fmt"
	"github.com/spf13/afero"
	"io"
	"os"
	"strings"
	"unicode/utf8"
)

const envPropertyPrefix = "${ENV:"
const envPropertySuffix = "}"

// LoadFile loads key/value pairs from a file
func LoadFile(fs afero.Fs, filename string) (map[string]string, error) {
	readFile, err := fs.Open(filename)
	if err != nil {
		return nil, err
	}
	defer func(readFile afero.File) {
		_ = readFile.Close()
	}(readFile)

	return Load(readFile)
}

// Load loads key/value pairs from a file
func Load(readFile io.Reader) (map[string]string, error) {
	reader := newLineReader(readFile)
	lines, err := reader.readLines()
	if err != nil {
		return nil, err
	}

	properties := map[string]string{}
	for _, line := range lines {
		keyLen := 0
		valueStart := len(line)
		hasSep := false
		precedingBackslash := false
		var c rune
		for _, c = range line {
			// need check if escaped.
			if (c == '=' || c == ':') && !precedingBackslash {
				valueStart = keyLen + 1
				hasSep = true
				break
			} else if (c == ' ' || c == '\t' || c == '\f') && !precedingBackslash {
				valueStart = keyLen + 1
				break
			}
			if c == '\\' {
				precedingBackslash = !precedingBackslash
			} else {
				precedingBackslash = false
			}
			keyLen++
		}
		for _, c = range line[valueStart:] {
			if c != ' ' && c != '\t' && c != '\f' {
				if !hasSep && (c == '=' || c == ':') {
					hasSep = true
				} else {
					break
				}
			}
			valueStart++
		}
		key, err := loadConvert([]byte(line), 0, keyLen)
		if err != nil {
			return nil, err
		}
		value, err := loadConvert([]byte(line), valueStart, len(line)-valueStart)
		if err != nil {
			return nil, err
		}
		properties[key] = value
	}
	return properties, nil
}

func loadConvert(in []byte, off int, len int) (string, error) {
	var aChar byte
	end := off + len
	start := off
	for off < end {
		aChar = in[off]
		off++
		if aChar == '\\' {
			break
		}
	}
	if off == end {
		// No backslash
		if len == 0 {
			return "", nil
		}
		return string(in[start : start+len]), nil
	}

	// backslash found at off - 1, rewind offset
	off--
	out := []byte{}
	if off-start != 0 {
		out = in[start:off]
	}

	for off < end {
		aChar = in[off]
		off++
		if aChar != '\\' {
			out = append(out, aChar)
			continue
		}
		// No need to bounds check since LineReader::readLine excludes
		// unescaped \s at the end of the line
		aChar = in[off]
		off++
		if aChar == 'u' {
			// Read the xxxx
			if off > end-4 {
				return "", errors.New("malformed \\uxxxx encoding")
			}
			var value int
			for i := 0; i < 4; i++ {
				aChar = in[off]
				off++
				switch aChar {
				case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9':
					value = (value << 4) + int(aChar) - '0'
				case 'a', 'b', 'c', 'd', 'e', 'f':
					value = (value << 4) + 10 + int(aChar) - 'a'
				case 'A', 'B', 'C', 'D', 'E', 'F':
					value = (value << 4) + 10 + int(aChar) - 'A'
				default:
					return "", errors.New("malformed \\uxxxx encoding")
				}
			}
			out = utf8.AppendRune(out, rune(value))
		} else {
			if aChar == 't' {
				aChar = '\t'
			} else if aChar == 'r' {
				aChar = '\r'
			} else if aChar == 'n' {
				aChar = '\n'
			} else if aChar == 'f' {
				aChar = '\f'
			}
			out = append(out, aChar)
		}
	}
	return string(out), nil
}

func Resolve(fs afero.Fs, filename string, key string) (string, error) {
	properties, err := LoadFile(fs, filename)
	if err != nil {
		return "", err
	}

	if value, ok := properties[key]; ok {
		if decodedValue, err := decodeEnvProperty(value); err != nil {
			return "", err
		} else {
			return decodedValue, nil
		}
	}

	return "", fmt.Errorf("there is no property named %s in %s", key, filename)
}

func decodeEnvProperty(value string) (string, error) {
	if strings.HasPrefix(value, envPropertyPrefix) {
		if !strings.HasSuffix(value, envPropertySuffix) {
			return "", fmt.Errorf("malformed property %s, does not end with }", value)
		}

		envVariable := strings.TrimSuffix(strings.TrimPrefix(value, envPropertyPrefix), envPropertySuffix)
		return findEnvironmentVariable(envVariable)
	}
	return value, nil
}

func findEnvironmentVariable(key string) (string, error) {
	for _, e := range os.Environ() {
		pair := strings.SplitN(e, "=", 2)
		if len(pair) < 2 {
			continue
		}

		if pair[0] == key {
			return pair[1], nil
		}
	}

	return "", fmt.Errorf("could not find environment variable: %s", key)
}

func Parse(args []string) (map[string]string, error) {
	properties := map[string]string{}
	for _, arg := range args {
		if !strings.Contains(arg, "=") {
			return nil, fmt.Errorf("property is malformed: %s", arg)
		}
		var kv = strings.SplitN(arg, "=", 2)
		key := strings.TrimSpace(kv[0])
		value := strings.TrimSpace(kv[1])
		if key == "config" {
			return nil, fmt.Errorf("cannot specify config using -D option (use --config)")
		}
		if key == "log.output-file" {
			return nil, fmt.Errorf("cannot specify server log using -D option (use --server-log-file)")
		}
		if key == "log.levels-file" {
			return nil, fmt.Errorf("cannot specify log levels using -D option (use --log-levels-file)")
		}
		properties[key] = value
	}

	return properties, nil
}

// LoadLines loads lines from a file, ignoring blank or comment lines
func LoadLines(fs afero.Fs, filename string) ([]string, error) {
	readFile, err := fs.Open(filename)
	if err != nil {
		return nil, err
	}
	defer func(readFile afero.File) {
		_ = readFile.Close()
	}(readFile)

	reader := newLineReader(readFile)
	return reader.readLines()
}

type lineReader struct {
	r       io.Reader
	byteBuf []byte
	lineBuf []byte
	inLimit int
	inOff   int
}

func newLineReader(r io.Reader) lineReader {
	return lineReader{
		r:       r,
		byteBuf: make([]byte, 8192),
		lineBuf: make([]byte, 0, 1024),
	}
}

func (r *lineReader) readLines() ([]string, error) {
	result := []string{}
	for {
		line, err := r.readLine()
		if err != nil {
			if errors.Is(err, io.EOF) {
				return result, nil
			}
			return nil, err
		}
		result = append(result, line)
	}
}

func (r *lineReader) readLine() (string, error) {
	// reset the slice size but preserve its capacity
	r.lineBuf = r.lineBuf[:0]
	off := r.inOff
	limit := r.inLimit

	skipWhiteSpace := true
	appendedLineBegin := false
	precedingBackslash := false

	var c byte
	var err error

	for {
		if off >= limit {
			limit, err = r.r.Read(r.byteBuf)
			if err != nil && !errors.Is(err, io.EOF) {
				return "", err
			}
			r.inLimit = limit
			if limit <= 0 {
				if len(r.lineBuf) == 0 {
					return "", io.EOF
				}
				if precedingBackslash {
					r.lineBuf = r.lineBuf[:len(r.lineBuf)-1]
				}
				return string(r.lineBuf), nil
			}
			off = 0
		}

		// byte & 0xFF is equivalent to calling a ISO8859-1 decoder.
		c = r.byteBuf[off] & 0xFF
		off++

		if skipWhiteSpace {
			if c == ' ' || c == '\t' || c == '\f' {
				continue
			}
			if !appendedLineBegin && (c == '\r' || c == '\n') {
				continue
			}
			skipWhiteSpace = false
			appendedLineBegin = false
		}

		if len(r.lineBuf) == 0 { // Still on a new logical line
			if c == '#' || c == '!' {
				// Comment, quickly consume the rest of the line

				// When checking for new line characters a range check,
				// starting with the higher bound ('\r') means one less
				// branch in the common case.
			commentLoop:
				for {
					var b byte
					for off < limit {
						b = r.byteBuf[off]
						off++
						if b <= '\r' && (b == '\r' || b == '\n') {
							break commentLoop
						}
					}
					if off == limit {
						limit, err = r.r.Read(r.byteBuf)
						if err != nil && !errors.Is(err, io.EOF) {
							return "", err
						}
						r.inLimit = limit
						if limit <= 0 {
							return "", io.EOF
						}
						off = 0
					}
				}
				skipWhiteSpace = true
				continue
			}
		}

		if c != '\n' && c != '\r' {
			r.lineBuf = append(r.lineBuf, c)

			// flip the preceding backslash flag
			if c == '\\' {
				precedingBackslash = !precedingBackslash
			} else {
				precedingBackslash = false
			}
		} else {
			// reached EOL
			if len(r.lineBuf) == 0 {
				skipWhiteSpace = true
				continue
			}
			if off >= limit {
				limit, err = r.r.Read(r.byteBuf)
				if err != nil && !errors.Is(err, io.EOF) {
					return "", err
				}
				r.inLimit = limit
				off = 0
				if limit <= 0 { // EOF
					if precedingBackslash {
						r.lineBuf = r.lineBuf[:len(r.lineBuf)-1]
					}
					return string(r.lineBuf), nil
				}
			}
			if precedingBackslash {
				// backslash at EOL is not part of the line
				r.lineBuf = r.lineBuf[:len(r.lineBuf)-1]
				// skip leading whitespace characters in the following line
				skipWhiteSpace = true
				appendedLineBegin = true
				precedingBackslash = false
				// take care not to include any subsequent \n
				if c == '\r' {
					if r.byteBuf[off] == '\n' {
						off++
					}
				}
			} else {
				r.inOff = off
				return string(r.lineBuf), nil
			}
		}
	}
}

package properties

import (
	"fmt"
	"slices"
	"strconv"
	"strings"
	"testing"

	"github.com/spf13/afero"
)

func TestLoad(t *testing.T) {
	t.Parallel()
	// actual FS to read testData
	filesystem := afero.NewOsFs()

	tests := map[string]struct {
		filename string
		input    string
		expected map[string]string
	}{
		"empty": {
			input:    " ",
			expected: map[string]string{},
		},
		"escapeSpace": {
			input: `key1=\ \ Value\u4e001, has leading and trailing spaces\  
            key2=Value\u4e002,\ does not have\ leading or trailing\ spaces
            key3=Value\u4e003,has,no,spaces
            key4=Value\u4e004, does not have leading spaces\  
            key5=\t\ \ Value\u4e005, has leading tab and no trailing spaces
            key6=\ \ Value\u4e006,doesnothaveembeddedspaces\ \ 
            \ key1\ test\ =key1, has leading and trailing spaces  
            key2\ test=key2, does not have leading or trailing spaces
            key3test=key3,has,no,spaces
            key4\ test\ =key4, does not have leading spaces  
            \t\ key5\ test=key5, has leading tab and no trailing spaces
            \ \ key6\ \ =\  key6,doesnothaveembeddedspaces  `,
			expected: map[string]string{
				"key1":         "  Value\u4e001, has leading and trailing spaces  ",
				"key2":         "Value\u4e002, does not have leading or trailing spaces",
				"key3":         "Value\u4e003,has,no,spaces",
				"key4":         "Value\u4e004, does not have leading spaces  ",
				"key5":         "\t  Value\u4e005, has leading tab and no trailing spaces",
				"key6":         "  Value\u4e006,doesnothaveembeddedspaces  ",
				" key1 test ":  "key1, has leading and trailing spaces  ",
				"key2 test":    "key2, does not have leading or trailing spaces",
				"key3test":     "key3,has,no,spaces",
				"key4 test ":   "key4, does not have leading spaces  ",
				"\t key5 test": "key5, has leading tab and no trailing spaces",
				"  key6  ":     "  key6,doesnothaveembeddedspaces  ",
			},
		},
		"trino": {
			input: `internal-communication.shared-secret=${ENV\:SHARED_SECRET}${ENV:ANOTHER_SECRET}
			unescapedChars= =: #!\u4e00AAAA
			escapedChars=\ \=\:\ \#\!\\u\4\e\0\0\A\A\A\A`,
			expected: map[string]string{
				"internal-communication.shared-secret": "${ENV:SHARED_SECRET}${ENV:ANOTHER_SECRET}",
				"unescapedChars":                       "=: #!\u4e00AAAA",
				"escapedChars":                         " =: #!\\u4e00AAAA",
			},
		},
		"input.txt": {
			filename: "testData/input.txt",
			expected: map[string]string{
				"key1": "value1",
				"key2": "abc\\defg\\",
				"key3": "value3",
				"key4": ":value4",
			},
		},
		"testData1": {
			filename: "testData/testData1",
			expected: map[string]string{
				"\\":            "key10=bar",
				"\\:key12":      "bar2",
				"key16 b":       " abcdef",
				"key14_asdfa":   "",
				"\\\\":          "key11=bar2",
				"key8notassign": "abcdef",
				"key17":         "#barbaz",
				"key15":         " abcdef",
				"keyabcdef":     "",
				"key13dialog.3": "",
				"key7":          "Symbol,SYMBOL_CHARSET ",
				"key6":          "WingDings,SYMBOL_CHARSET \\abc",
				"key5":          "==Arial,ANSI_CHARSET",
				"key3":          "",
				"key2":          "= abcde",
				"key1":          "value1",
				"key9 Term":     "ABCDE",
				"key0":          "abcd",
			},
		},
		"testData2": {
			filename: "testData/testData2",
			expected: map[string]string{
				"key1": "-monotype-timesnewroman-regular-r---*-%d-*-*-p-*-iso8859-1serif.1a-monotype-timesnewroman-regular-r-normal--*-%d-*-*-p-*-iso8859-2serif.2a-b&h-LucidaBrightLat4-Normal-r-normal--*-%d-*-*-p-*-iso8859-4serif.3a-monotype-times-regular-r-normal--*-%d-*-*-p-*-iso8859-5serif.4a-monotype-timesnewromangreek-regular-r-normal--*-%d-*-*-p-*-iso8859-7serif.5a-monotype-times-regular-r-normal--*-%d-*-*-p-*-iso8859-9serif.6a-monotype-times-regular-r-normal--*-%d-*-*-p-*-iso8859-15serif.7a-hanyi-ming-medium-r-normal--*-%d-*-*-m-*-big5-1serif.8a-sun-song-medium-r-normal--*-%d-*-*-c-*-gb2312.1980-0serif.9a-ricoh-hgminchol-medium-r-normal--*-%d-*-*-m-*-jisx0201.1976-0serif.10a-ricoh-hgminchol-medium-r-normal--*-%d-*-*-m-*-jisx0208.1983-0serif.11a-ricoh-heiseimin-w3-r-normal--*-%d-*-*-m-*-jisx0212.1990-0serif.12a-hanyang-myeongjo-medium-r-normal--*-%d-*-*-m-*-ksc5601.1992-3serif.13a-urw-itczapfdingbats-medium-r-normal--*-%d-*-*-p-*-sun-fontspecificserif.14a-*-symbol-medium-r-normal--*-%d-*-*-p-*-sun-fontspecificbserif.italic.0=-monotype-timesbnewbroman-regular-i---*-%d-*-*-p-*-iso8859-1bserif.italic.1=-monotype-timesbnewbroman-regular-i-normal-italic-*-%d-*-*-p-*-iso8859-2",
				"key2": "-b&h-LucidaBrightLat4-normal-i-normal-Italic-*-%d-*-*-p-*-iso8859-4",
			},
		},
	}

	for name, tc := range tests {
		t.Run(name, func(t *testing.T) {
			var actual map[string]string
			var err error
			if tc.filename != "" {
				actual, err = LoadFile(filesystem, tc.filename)
			} else {
				actual, err = Load(strings.NewReader(tc.input))
			}
			if err != nil {
				t.Fatalf("Expected Load NOT to return an error, got: %v", err)
			}
			different := []string{}
			missing := []string{}
			additional := []string{}
			for key, expectedValue := range tc.expected {
				actualValue, ok := actual[key]
				if !ok {
					missing = append(missing, key)
				} else if expectedValue != actualValue {
					different = append(different, fmt.Sprintf("Expected value for key %s to be `%s` but got `%s`", key, expectedValue, actualValue))
				}
			}
			for key, _ := range actual {
				_, ok := tc.expected[key]
				if !ok {
					additional = append(additional, key)
				}
			}
			if len(additional) != 0 {
				slices.Sort(additional)
				t.Errorf("Load result contains unexpected keys:\n%s", strings.Join(additional, "\n"))
			}
			if len(missing) != 0 {
				slices.Sort(missing)
				t.Errorf("Load result missing keys:\n%s", strings.Join(missing, "\n"))
			}
			if len(different) != 0 {
				slices.Sort(different)
				t.Errorf("Load result are different:\n%s", strings.Join(different, "\n"))
			}
		})
	}
}

func TestLoadMalformed(t *testing.T) {
	t.Parallel()
	tests := []string{
		"b=\\u012\n",
		"b=\\u01\n",
		"b=\\u0\n",
		"b=\\u\n",
		"a=\\u0123\nb=\\u012\n",
		"a=\\u0123\nb=\\u01\n",
		"a=\\u0123\nb=\\u0\n",
		"a=\\u0123\nb=\\u\n",
		"b=\\u012xyz\n",
		"b=x\\u012yz\n",
		"b=xyz\\u012\n",
	}
	for i, tc := range tests {
		t.Run("malformed "+strconv.Itoa(i), func(t *testing.T) {

			_, err := Load(strings.NewReader(tc))
			if err == nil {
				t.Fatalf("Expected Load to return an error, got nil")
			}
			expectedError := "malformed \\uxxxx encoding"
			if err.Error() != expectedError {
				t.Fatalf("Expected Load to return an error `%s`, got: %v", expectedError, err)
			}
		})
	}
}

func TestResolveMissing(t *testing.T) {
	t.Parallel()
	fs := afero.NewMemMapFs()

	_, err := Resolve(fs, "/etc/trino/config.properties", "propertyName")

	if err == nil {
		t.Fatalf("Expected Resolve to return an error")
	}
	expected := "open /etc/trino/config.properties: file does not exist"
	if err.Error() != expected {
		t.Fatalf("Expected Resolve to return %s, but got %v", expected, err)
	}
}

func TestResolveInvalid(t *testing.T) {
	t.Parallel()
	fs := afero.NewMemMapFs()

	file, _ := fs.Create("/etc/trino/config.properties")
	file.WriteString("key=value")
	file.Close()

	_, err := Resolve(fs, "/etc/trino/config.properties", "propertyName")

	if err == nil {
		t.Fatalf("Expected Resolve to return an error")
	}
	expected := "there is no property named propertyName in /etc/trino/config.properties"
	if err.Error() != expected {
		t.Fatalf("Expected Resolve to return %s, but got %v", expected, err)
	}
}

func TestResolveEnvMissing(t *testing.T) {
	t.Parallel()
	fs := afero.NewMemMapFs()

	file, _ := fs.Create("/etc/trino/config.properties")
	file.WriteString("propertyName=${ENV:MISSING}")
	file.Close()

	_, err := Resolve(fs, "/etc/trino/config.properties", "propertyName")

	if err == nil {
		t.Fatalf("Expected Resolve to return an error")
	}
	expected := "could not find environment variable: MISSING"
	if err.Error() != expected {
		t.Fatalf("Expected Resolve to return %s, but got %v", expected, err)
	}
}

func TestResolve(t *testing.T) {
	fs := afero.NewMemMapFs()

	file, _ := fs.Create("/etc/trino/config.properties")
	file.WriteString("propertyName=propertyValue")
	file.Close()

	actual, err := Resolve(fs, "/etc/trino/config.properties", "propertyName")

	if err != nil {
		t.Fatalf("Expected Resolve NOT to return an error, got: %v", err)
	}
	expected := "propertyValue"
	if actual != expected {
		t.Fatalf("Expected Resolve to return %s, but got %v", expected, actual)
	}
}

func TestParse(t *testing.T) {
	t.Parallel()
	input := []string{
		"key=value",
		"key=duplicate",
		"other=value",
	}
	actual, err := Parse(input)

	if err != nil {
		t.Fatalf("Expected Parse NOT to return an error, got: %v", err)
	}
	expected := map[string]string{
		"key":   "duplicate",
		"other": "value",
	}
	if !equalMap(expected, actual) {
		t.Fatalf("Expected Parse to return %s, but got %v", expected, actual)
	}
}

func equalMap[M1, M2 ~map[K]V, K, V comparable](m1 M1, m2 M2) bool {
	if len(m1) != len(m2) {
		return false
	}
	for k, v1 := range m1 {
		if v2, ok := m2[k]; !ok || v1 != v2 {
			return false
		}
	}
	return true
}

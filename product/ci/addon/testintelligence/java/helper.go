// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package java

import (
	"bufio"
	"encoding/xml"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/mattn/go-zglob"
	"github.com/wings-software/portal/commons/go/lib/filesystem"
	"github.com/wings-software/portal/product/ci/common/external"
	"go.uber.org/zap"
)

var (
	getWorkspace = external.GetWrkspcPath
	javaAgentArg = "-javaagent:/addon/bin/java-agent.jar=%s"
)

// get list of all file paths matching a provided regex
func getFiles(path string) ([]string, error) {
	fmt.Println("path: ", path)
	matches, err := zglob.Glob(path)
	if err != nil {
		return []string{}, err
	}
	return matches, err
}

// Checks if a given XML file has an "harnessArgLine".
//
// Args:
//     filePath (string): The absolute path to XML file to be checked.
//
// Returns:
//     bool:  True if the Harness tag exists, false otherwise
//     error: error if there's any, nil otherwise

func checkHarnessTag(filePath string) (bool, error) {
	data, err := os.ReadFile(filePath)
	if err != nil {
		log.Errorw("Failed to read file at: ", filePath)
		return false, err
	}
	harnessTag := "harnessArgLine" // FIXME: Make this a global constant or a flag.
	xmlDecoder := xml.NewDecoder(strings.NewReader(string(data)))
	for {
		token, err := xmlDecoder.Token()
		if err != nil {
			if err == io.EOF {
				return false, nil
			}
			return false, err
		}
		if se, ok := token.(xml.StartElement); ok {
			if se.Name.Local == harnessTag {
				return true, nil
			}
		}
	}
}

// Scan all XML files in the workspace and detect if any of them have harnessArgLine tag.

func DetectHarnessTag() (bool, error) {
	// Get all the XML files from $HARNESS_WORKSPACE
	wp, _ := GetWrkspcPath()
	files, _ := getFiles(fmt.Sprintf("%s/**/*.xml", wp))
	foundHarnessTag := false
	err := nil

	// Check each XML file for the harnessArgLine tag.
	for _, f := range files {
		absPath, err := filepath.Abs(f)
		if err == nil {
			fmt.Println("Checking file: ", absPath)
			log.Infow(fmt.Sprintf("Checking XML file %s for Harness tag", absPath))
			found, _ := checkHarnessTag(absPath)
			if found {
				foundHarnessTag = true
			}
		} else {
			log.Errorw("Failed to absolute path. Error", err)
		}
	}
	return foundHarnessTag, err
}

// detect java packages by reading all the files and parsing their package names
func DetectPkgs(log *zap.SugaredLogger, fs filesystem.FileSystem) ([]string, error) {
	plist := []string{}
	excludeList := []string{"com.google"} // exclude any instances of these packages from the package list
	wp, err := getWorkspace()
	if err != nil {
		return plist, err
	}
	files, err := getFiles(fmt.Sprintf("%s/**/*.java", wp))
	if err != nil {
		return plist, err
	}
	kotlinFiles, err := getFiles(fmt.Sprintf("%s/**/*.kt", wp))
	if err != nil {
		return plist, err
	}
	// Create a list with all *.java and *.kt file paths
	files = append(files, kotlinFiles...)
	fmt.Println("files: ", files)
	m := make(map[string]struct{})
	for _, f := range files {
		absPath, err := filepath.Abs(f)
		if err != nil {
			log.Errorw("could not get absolute path", "file_name", f, err)
			continue
		}
		// TODO: (Vistaar)
		// This doesn't handle some special cases right now such as when there is a package
		// present in a multiline comment with multiple opening and closing comments.
		// We will require to read all the lines together to handle this.
		err = fs.ReadFile(absPath, func(fr io.Reader) error {
			scanner := bufio.NewScanner(fr)
			commentOpen := false
			for scanner.Scan() {
				l := strings.TrimSpace(scanner.Text())
				if strings.Contains(l, "/*") {
					commentOpen = true
				}
				if strings.Contains(l, "*/") {
					commentOpen = false
					continue
				}
				if commentOpen || strings.HasPrefix(l, "//") {
					continue
				}
				prev := ""
				pkg := ""
				for _, token := range strings.Fields(l) {
					if prev == "package" {
						pkg = token
						break
					}
					prev = token
				}
				if pkg != "" {
					pkg = strings.TrimSuffix(pkg, ";")
					tokens := strings.Split(pkg, ".")
					prefix := false
					for _, exclude := range excludeList {
						if strings.HasPrefix(pkg, exclude) {
							log.Infow(fmt.Sprintf("Found package: %s having same package prefix as: %s. Excluding this package from the list...", pkg, exclude))
							prefix = true
							break
						}
					}
					if !prefix {
						pkg = tokens[0]
						if len(tokens) > 1 {
							pkg = pkg + "." + tokens[1]
						}
					}
					if pkg == "" {
						continue
					}
					if _, ok := m[pkg]; !ok {
						plist = append(plist, pkg)
						m[pkg] = struct{}{}
					}
					return nil
				}
			}
			if err := scanner.Err(); err != nil {
				log.Errorw(fmt.Sprintf("could not scan all the files. Error: %s", err))
				return err
			}
			return nil
		})
		if err != nil {
			log.Errorw("had issues while trying to auto detect java packages", err)
		}
	}
	return plist, nil
}

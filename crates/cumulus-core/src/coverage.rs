use quick_xml::events::Event;
use quick_xml::reader::Reader;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Path;

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct CoverageEntry {
    pub file: String,
    pub covered_lines: Vec<usize>,
    pub missed_lines: Vec<usize>,
}

pub fn parse_jacoco_xml(xml_content: &str) -> Vec<CoverageEntry> {
    let mut entries = Vec::new();
    let mut reader = Reader::from_str(xml_content);
    reader.config_mut().trim_text(true);

    let mut current_package = String::new();
    let mut current_sourcefile = String::new();
    let mut covered_lines = Vec::new();
    let mut missed_lines = Vec::new();

    let mut buf = Vec::new();

    loop {
        match reader.read_event_into(&mut buf) {
            Ok(Event::Start(ref e)) | Ok(Event::Empty(ref e)) => match e.name().as_ref() {
                b"package" => {
                    for attr in e.attributes().flatten() {
                        if attr.key.as_ref() == b"name" {
                            current_package = attr.decode_and_unescape_value(reader.decoder()).unwrap_or_default().to_string();
                        }
                    }
                }
                b"sourcefile" => {
                    for attr in e.attributes().flatten() {
                        if attr.key.as_ref() == b"name" {
                            current_sourcefile = attr.decode_and_unescape_value(reader.decoder()).unwrap_or_default().to_string();
                        }
                    }
                    covered_lines.clear();
                    missed_lines.clear();
                }
                b"line" => {
                    let mut line_nr = 0;
                    let mut missed_instructions = 0;
                    let mut covered_instructions = 0;

                    for attr in e.attributes().flatten() {
                        match attr.key.as_ref() {
                            b"nr" => {
                                line_nr = attr.decode_and_unescape_value(reader.decoder()).unwrap_or_default().parse().unwrap_or(0);
                            }
                            b"mi" => {
                                missed_instructions = attr.decode_and_unescape_value(reader.decoder()).unwrap_or_default().parse().unwrap_or(0);
                            }
                            b"ci" => {
                                covered_instructions = attr.decode_and_unescape_value(reader.decoder()).unwrap_or_default().parse().unwrap_or(0);
                            }
                            _ => {}
                        }
                    }

                    if line_nr > 0 {
                        if covered_instructions > 0 {
                            covered_lines.push(line_nr);
                        } else if missed_instructions > 0 {
                            missed_lines.push(line_nr);
                        }
                    }
                }
                _ => {}
            },
            Ok(Event::End(ref e)) => {
                if e.name().as_ref() == b"sourcefile" {
                    if !current_sourcefile.is_empty() {
                        let full_rel = if current_package.is_empty() {
                            current_sourcefile.clone()
                        } else {
                            format!("{}/{}", current_package, current_sourcefile)
                        };
                        entries.push(CoverageEntry {
                            file: full_rel,
                            covered_lines: covered_lines.clone(),
                            missed_lines: missed_lines.clone(),
                        });
                    }
                }
            }
            Ok(Event::Eof) => break,
            Err(_) => break,
            _ => {}
        }
        buf.clear();
    }

    entries
}

pub fn parse_coverage_file(file_path: &Path) -> Vec<CoverageEntry> {
    let content = match fs::read_to_string(file_path) {
        Ok(c) => c,
        Err(_) => return Vec::new(),
    };
    parse_jacoco_xml(&content)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_jacoco_xml() {
        let xml = r#"
<report name="demo">
  <package name="com/example/demo">
    <sourcefile name="App.java">
      <line nr="10" mi="0" ci="5"/>
      <line nr="15" mi="3" ci="0"/>
    </sourcefile>
  </package>
</report>
"#;
        let entries = parse_jacoco_xml(xml);
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0].file, "com/example/demo/App.java");
        assert_eq!(entries[0].covered_lines, vec![10]);
        assert_eq!(entries[0].missed_lines, vec![15]);
    }
}

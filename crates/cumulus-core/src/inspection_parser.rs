use crate::log_parser::DiagnosticEntry;
use quick_xml::events::Event;
use quick_xml::reader::Reader;

pub fn parse_checkstyle_xml(xml_content: &str) -> Vec<DiagnosticEntry> {
    let mut diagnostics = Vec::new();
    let mut reader = Reader::from_str(xml_content);
    reader.config_mut().trim_text(true);

    let mut current_file = String::new();
    let mut buf = Vec::new();

    loop {
        match reader.read_event_into(&mut buf) {
            Ok(Event::Start(ref e)) | Ok(Event::Empty(ref e)) => match e.name().as_ref() {
                b"file" => {
                    for attr in e.attributes().flatten() {
                        if attr.key.as_ref() == b"name" {
                            current_file = attr.decode_and_unescape_value(reader.decoder()).unwrap_or_default().to_string();
                        }
                    }
                }
                b"error" => {
                    let mut line_num = 1;
                    let mut col_num = None;
                    let mut severity = "WARN".to_string();
                    let mut message = String::new();

                    for attr in e.attributes().flatten() {
                        match attr.key.as_ref() {
                            b"line" => {
                                line_num = attr.decode_and_unescape_value(reader.decoder()).unwrap_or_default().parse().unwrap_or(1);
                            }
                            b"column" => {
                                col_num = attr.decode_and_unescape_value(reader.decoder()).unwrap_or_default().parse().ok();
                            }
                            b"severity" => {
                                severity = attr.decode_and_unescape_value(reader.decoder()).unwrap_or_default().to_uppercase();
                            }
                            b"message" => {
                                message = attr.decode_and_unescape_value(reader.decoder()).unwrap_or_default().to_string();
                            }
                            _ => {}
                        }
                    }

                    if !current_file.is_empty() && !message.is_empty() {
                        diagnostics.push(DiagnosticEntry {
                            file: current_file.clone(),
                            line: line_num,
                            col: col_num,
                            message,
                            severity,
                        });
                    }
                }
                _ => {}
            },
            Ok(Event::Eof) => break,
            Err(_) => break,
            _ => {}
        }
        buf.clear();
    }

    diagnostics
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_checkstyle_xml() {
        let xml = r#"
<checkstyle version="10.0">
  <file name="/src/main/java/com/example/App.java">
    <error line="15" column="5" severity="warning" message="Missing a Javadoc comment." source="com.puppycrawl.tools.checkstyle.checks.javadoc.MissingJavadocMethodCheck"/>
  </file>
</checkstyle>
"#;
        let diags = parse_checkstyle_xml(xml);
        assert_eq!(diags.len(), 1);
        assert_eq!(diags[0].file, "/src/main/java/com/example/App.java");
        assert_eq!(diags[0].line, 15);
        assert_eq!(diags[0].col, Some(5));
        assert_eq!(diags[0].severity, "WARNING");
    }
}

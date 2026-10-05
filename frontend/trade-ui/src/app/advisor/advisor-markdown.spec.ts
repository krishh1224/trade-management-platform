import { renderAdvisorMarkdown } from './advisor-markdown';

describe('renderAdvisorMarkdown', () => {
  it('escapes HTML so model output cannot inject markup', () => {
    const html = renderAdvisorMarkdown('<img src=x onerror="alert(1)"> **hi**');

    expect(html).not.toContain('<img');
    expect(html).toContain('&lt;img src=x onerror=&quot;alert(1)&quot;&gt;');
    expect(html).toContain('<strong>hi</strong>');
  });

  it('renders paragraphs, bullet lists and numbered lists', () => {
    const html = renderAdvisorMarkdown('Your holdings:\n- **AAPL** 7 shares\n- ITC.NS 150 shares\n\n1. First\n2. Second');

    expect(html).toBe(
      '<p>Your holdings:</p>' +
        '<ul><li><strong>AAPL</strong> 7 shares</li><li>ITC.NS 150 shares</li></ul>' +
        '<ol><li>First</li><li>Second</li></ol>'
    );
  });

  it('renders a table and skips the separator row', () => {
    const html = renderAdvisorMarkdown('| Symbol | Value (INR) |\n|---|---:|\n| AAPL | 2,24,665.92 |');

    expect(html).toBe(
      '<table><thead><tr><th>Symbol</th><th>Value (INR)</th></tr></thead>' +
        '<tbody><tr><td>AAPL</td><td>2,24,665.92</td></tr></tbody></table>'
    );
  });

  it('renders the italic disclaimer and inline code but not symbols with dots or underscores', () => {
    const html = renderAdvisorMarkdown('_This is educational information, not investment advice._ Use `get_quotes` for ITC.NS');

    expect(html).toContain('<em>This is educational information, not investment advice.</em>');
    expect(html).toContain('<code>get_quotes</code>');
    expect(html).toContain('ITC.NS');
  });

  it('keeps single line breaks inside a paragraph', () => {
    expect(renderAdvisorMarkdown('line one\nline two')).toBe('<p>line one<br>line two</p>');
  });
});

import { renderAdvisorMarkdown } from './advisor-markdown';

describe('renderAdvisorMarkdown', () => {
  it('escapes raw html before formatting', () => {
    const html = renderAdvisorMarkdown('<img src=x onerror="alert(1)"> **hi**');

    expect(html).toContain('&lt;img src=x onerror=&quot;alert(1)&quot;&gt;');
    expect(html).toContain('<strong>hi</strong>');
    expect(html).not.toContain('<img');
  });

  it('renders bullet and numbered lists', () => {
    const html = renderAdvisorMarkdown('Your holdings:\n- **AAPL** 7 shares\n- ITC.NS 150 shares\n\n1. First\n2. Second');

    expect(html).toContain('<ul>');
    expect(html).toContain('<li><strong>AAPL</strong> 7 shares</li>');
    expect(html).toContain('<ol>');
    expect(html).toContain('<li>Second</li>');
  });

  it('renders tables', () => {
    const html = renderAdvisorMarkdown('| Symbol | Value (INR) |\n|---|---:|\n| AAPL | 2,24,665.92 |');

    expect(html).toContain('<table>');
    expect(html).toContain('<th>Symbol</th>');
    expect(html).toContain('<td>2,24,665.92</td>');
  });

  it('renders italics and inline code', () => {
    const html = renderAdvisorMarkdown('_This is educational information, not investment advice._ Use `get_quotes` for ITC.NS');

    expect(html).toContain('<em>This is educational information, not investment advice.</em>');
    expect(html).toContain('<code>get_quotes</code>');
  });

  it('turns line breaks inside a paragraph into br tags', () => {
    expect(renderAdvisorMarkdown('line one\nline two')).toBe('<p>line one<br>line two</p>');
  });
});
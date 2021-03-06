import json
import sys
import os.path
from bs4 import BeautifulSoup

# This script inject code examples into the HTML output according to the manifest file
# generated by the CodeExampleManifestDoclet for javadoc,

if len(sys.argv) != 4:
  print "Usage: " + sys.argv[0] + " <path/to/examplesManifest.json> <path/to/examples/root/> <path/to/javadoc/html/output/>"
  sys.exit(1)

# HTML class used for the code examples
CODE_EXAMPLE_CLASS = 'codeExample'

def load_example(entity, examples_base_path):
  path = entity['examplePath']
  if not path:
    return None
  with open(os.path.join(examples_base_path, path)) as example_file:
    return example_file.read()


def make_code_example_pre(soup, example):
  pre = soup.new_tag('pre', **{'class': CODE_EXAMPLE_CLASS})
  code = soup.new_tag('code')
  code.string = example
  pre.append(code)
  return pre


def paste_type_example(soup, type, example):
  # This is for the top-level examples after the class comment.
  # We will paste the example after the description block.
  description = soup.find('div', {'class': 'description'})
  if not description:
    raise Exception('No description div found!')

  existing_example = description.parent.find('div', {'class': 'example'})

  example_pre = make_code_example_pre(soup, example)
  
  example_block = BeautifulSoup('''
<div class="example">
  <ul class="blockList">
    <li class="blockList">
      <!-- ======== CODE EXAMPLE ======== -->
      <section role="region">
        <ul class="blockList">
          <li class="blockList">
            <a id="top_example"><!-- --></a>
            <h3>Example</h3>
            ''' + str(example_pre) + '''
          </li>
        </ul>
      </section>
    </li>
  </ul>
</div>
''', 'html.parser')
  
  if existing_example:
    print 'Replacing existing code example for ' + type['name']
    existing_example.replace_with(example_block)
  else:
    print 'Pasting code example for ' + type['name']
    description.insert_after(example_block)


def paste_method_example(soup, method, example):
  # This is for the method examples in the method details section.
  # We can use the <a> anchor ids to find the spot.
  anchor_name = method['anchorName']
  method_anchor = soup.find('a', id=method['anchorName'])
  if method_anchor == -1:
    raise Exception('No method anchor found for ' + anchor_name)

  detail_block_list = method_anchor.find_next_sibling('ul', {'class': 'blockList'})
  if not detail_block_list:
    detail_block_list = method_anchor.find_next_sibling('ul', {'class': 'blockListLast'})

  if not detail_block_list:
    raise Exception('No block list found for ' + anchor_name)

  example_pre = make_code_example_pre(soup, example)

  existing_example = detail_block_list.find('pre', **{'class': CODE_EXAMPLE_CLASS})

  if existing_example:
    print 'Replacing existing code example for ' + method['name']
    existing_example.replace_with(example_pre)
    return

  # Attach to the list where parameters, return, etc. are listed -- or
  # create a list if none exists (i.e. method has no params or return)
  dl = detail_block_list.find('dl')
  if not dl:
    dl = soup.new_tag('dl')
    inner_detail_list = detail_block_list.find('li', {'class': 'blockList'})
    inner_detail_list.append(dl)

  dt = BeautifulSoup(
'''
<dt><span class="exampleLabel">Example:</span></dt>
<dd>''' + str(example_pre) + '''</dd>
''', 'html.parser')

  print 'Pasting code example for ' + method['name']
  dl.insert(0, dt)


def load_manifest(manifest_path):
  print 'Loading manifest file: ' + manifest_path
  with open(manifest_path) as manifest_file:
    return json.load(manifest_file)


def paste_examples(manifest, html_base_path, examples_base_path):
  # Read the manifest and paste the specified examples into the corresponding html files.
  for type in manifest:
    name = type['name']
    html_path = os.path.join(html_base_path, type['htmlPath'])
    with open(html_path) as html_file:
      soup = BeautifulSoup(html_file, 'html.parser')

    type_example = load_example(type, examples_base_path)
    if type_example:
      paste_type_example(soup, type, type_example)

    for method in type['methods']:
      method_example = load_example(method, examples_base_path)
      paste_method_example(soup, method, method_example)

    with open(html_path, "w") as file:
      file.write(str(soup))
      print 'Written to: ' + html_path


manifest_path = sys.argv[1]
examples_base_path = sys.argv[2]
html_base_path = sys.argv[3]

print 'Pasting code examples...'
manifest = load_manifest(manifest_path)
paste_examples(manifest, html_base_path, examples_base_path)
print 'Done pasting code examples.'

import {shallowMount} from '@vue/test-utils';
import EditorTransformation from './_editor-transformation.vue';
import {ImportDataset} from '../types';


describe('EditorTransformation component', () => {
  const wrapper = shallowMount(EditorTransformation, {
    props: {
      name: 'test',
      generic: false,
      bodyType: 'xquery',
      body: 'target-path\ttarget-node\tsource-node\tvalue\n/\tead\t/\t',
      comments: 'test',
      hasParams: false,
      dataset: {id: 'test'} as ImportDataset,
      config: {input: 'input'},
      initPreviewing: null,
    }
  });

  test('switching type keeps each type\'s body separate', async () => {
    const select = wrapper.find('#transformation-type');
    await select.setValue('json');
    expect(wrapper.vm.data.bodyType).toBe('json');
    expect(wrapper.vm.data.body).toBe('');

    wrapper.vm.data.body = 'op\tcolumns\tto\texpr\n';
    await select.setValue('xquery');
    expect(wrapper.vm.data.body).toBe('target-path\ttarget-node\tsource-node\tvalue\n/\tead\t/\t');

    await select.setValue('json');
    expect(wrapper.vm.data.body).toBe('op\tcolumns\tto\texpr\n');
  });
});

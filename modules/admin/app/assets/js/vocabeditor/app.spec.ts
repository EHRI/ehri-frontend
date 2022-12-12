
import { mount } from '@vue/test-utils';
import App from './app.vue';

jest.mock('./api');

describe('App', () => {
  // Inspect the raw component options
  it('has data', () => {
    expect(typeof App).toBe('object')
  })
});

describe('Mounted App', () => {
  // NB: note full mount here instead of shallowMount. This is because
  // we have mocked the whole data API so it should load all
  // components.
  const wrapper = mount(App, {
    props: {
      service: {},
      config: {
        vocabId: "foo"
      }
    }
  });

  test('is a Vue instance', () => {
    expect(wrapper.isVisible()).toBeTruthy()
    expect(wrapper.find("#vocab-editor-listnav .vocab-editor-concept").exists()).toBe(true);
  });
})

import {mount} from '@vue/test-utils';
import App from './app.vue';
import {ConfigType} from './types';


describe('App', () => {
  // Inspect the raw component options
  it('has data', () => {
    expect(typeof App).toBe('object')
  })
});

describe('Mounted App', () => {
  const wrapper = mount(App, {
    props: {
      service: {}, config: {
        repoId: "foo"
      } as ConfigType
    }
  });

  test('is a Vue instance', () => {
    expect(wrapper.isVisible()).toBeTruthy()
  })
})
